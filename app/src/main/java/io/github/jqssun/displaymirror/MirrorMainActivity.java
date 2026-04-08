package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import io.github.jqssun.displaymirror.job.AcquireShizuku;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import com.topjohnwu.superuser.Shell;

import rikka.shizuku.Shizuku;

public class MirrorMainActivity extends AppCompatActivity {

    static {
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10));
    }

    public static final int REQUEST_CODE_MEDIA_PROJECTION = 1001;
    public static final int REQUEST_RECORD_AUDIO_PERMISSION = 1002;
    public static final int REQUEST_IMPORT_APK = 1003;
    public static final String TAG = "MirrorMainActivity";

    private long lastCheckTime = 0;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            State.resumeJob();
        } else {
            State.log("Unknown permission request code: " + requestCode);
        }
    }

    private void _onRequestShizukuPermissionsResult(int requestCode, int grantResult) {
        if (requestCode == AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE) {
            State.log("Shizuku permission result: " + (grantResult == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
            State.resumeJob();
        } else {
            State.log("Unknown Shizuku request code: " + requestCode);
        }
    }

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER =
        this::_onRequestShizukuPermissionsResult;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Failed to add hidden API exemption: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        State.setCurrentActivity(this);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        boolean doNotAutoStartMoonlight = getIntent().getBooleanExtra("DoNotAutoStartMoonlight", false);
        if (doNotAutoStartMoonlight) {
            Pref.doNotAutoStartMoonlight = doNotAutoStartMoonlight;
        }

        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        Shizuku.addBinderReceivedListenerSticky(_binderReceivedListener);
        Shizuku.addBinderDeadListener(_binderDeadListener);

        setContentView(R.layout.activity_main);

        // Setup bottom navigation
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);

        State.uiState.observe(this, state -> {});
    }

    private void _ensureAccessibilityServiceStarted() {
        if (TouchpadAccessibilityService.isAccessibilityServiceEnabled(this)) {
            Intent serviceIntent = new Intent(this, TouchpadAccessibilityService.class);
            this.startService(serviceIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        State.setCurrentActivity(this);
        refresh();
    }

    private final Shizuku.OnBinderReceivedListener _binderReceivedListener = () -> {
        State.log("Shizuku binder received");
    };

    private final Shizuku.OnBinderDeadListener _binderDeadListener = () -> {
        State.log("Shizuku binder DIED");
        io.github.jqssun.displaymirror.shizuku.ServiceUtils.invalidate();
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        Shizuku.removeBinderReceivedListener(_binderReceivedListener);
        Shizuku.removeBinderDeadListener(_binderDeadListener);
        State.setCurrentActivity(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMPORT_APK) {
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    try {
                        String err = ApkImporter.importFromApk(this, uri);
                        if (err == null) {
                            Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show();
                            State.log("DisplayLink APK imported successfully");
                        } else {
                            Toast.makeText(this, getString(R.string.import_failed, err), Toast.LENGTH_LONG).show();
                            State.log("APK import error: " + err);
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, getString(R.string.import_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                        State.log("APK import exception: " + e.getMessage());
                    }
                    refresh();
                }
            }
            return;
        }
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                State.log("User granted screen projection permission");
                lastCheckTime = System.currentTimeMillis();
                if (SunshineService.instance == null) {
                    Intent sunshineServiceIntent = new Intent(this, SunshineService.class);
                    sunshineServiceIntent.putExtra("data", data);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(sunshineServiceIntent);
                    } else {
                        startService(sunshineServiceIntent);
                    }
                    State.log("Starting SunshineService");
                } else {
                    MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    if (mediaProjectionManager == null) return;
                    State.setMediaProjection(mediaProjectionManager.getMediaProjection(RESULT_OK, data));
                    if (State.getMediaProjection() == null) { State.resumeJob(); return; }
                    State.getMediaProjection().registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            super.onStop();
                            State.log("MediaProjection onStop callback");
                        }
                    }, null);
                    State.resumeJob();
                }
            } else {
                State.log("User denied screen projection permission");
                refresh();
                State.resumeJob();
            }
        }
    }

    public void startMirroring() {
        AcquireShizuku.fixRootShizuku();
        if (!Pref.getDisableAccessibility()) {
            _ensureAccessibilityServiceStarted();
        }
        if (SunshineService.instance == null) {
            startMediaProjectionService();
        } else {
            State.log("SunshineService already running");
            refresh();
        }
    }

    public void startMediaProjectionService() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) this.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            Intent captureIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            } else {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            }
            MirrorMainActivity mirrorMainActivity = State.getCurrentActivity();
            if (mirrorMainActivity != null) {
                mirrorMainActivity.startActivityForResult(captureIntent, MirrorMainActivity.REQUEST_CODE_MEDIA_PROJECTION);
            }
        } else {
            throw new RuntimeException("Failed to get MediaProjectionManager service");
        }
    }

    public void refresh() {
        MirrorUiState current = State.uiState.getValue();
        if (current != null && current.errorStatusText != null) {
            return;
        }
        boolean singleAppMode = Pref.getSingleAppMode();
        boolean useTouchscreen = Pref.getUseTouchscreen();

        boolean isScreenMirroring = State.mirrorVirtualDisplay != null ||
                                   State.displaylinkState.getVirtualDisplay() != null ||
                                   State.lastSingleAppDisplay != 0;

        MirrorUiState newUiState = new MirrorUiState();

        if (SunshineService.instance == null) {
            newUiState.mirrorStatusText = getString(R.string.status_idle);
            newUiState.startBtnVisibility = true;

            newUiState.screenOffBtnVisibility = false;
            newUiState.touchScreenBtnVisibility = false;
        } else if (isScreenMirroring) {
            newUiState.mirrorStatusText = getString(R.string.status_projecting);

            newUiState.stopBtnVisibility = true;
            newUiState.screenOffBtnVisibility = ShizukuUtils.hasPermission();
            newUiState.touchScreenBtnVisibility = singleAppMode;

            if (singleAppMode) {
                newUiState.touchScreenBtnText = useTouchscreen ? getString(R.string.touchscreen) : getString(R.string.touchpad);
            }
        } else {
            newUiState.mirrorStatusText = getString(R.string.status_connect_display);
            try {
                for (String ip : SunshineService.getAllWifiIpAddresses(this)) {
                    newUiState.mirrorStatusText += "\n\nIP: ";
                    newUiState.mirrorStatusText += ip;
                }
            } catch(Throwable e) {
                // ignore
            }

            newUiState.stopBtnVisibility = true;
            newUiState.screenOffBtnVisibility = false;
            newUiState.touchScreenBtnVisibility = false;
        }

        State.uiState.setValue(newUiState);
    }

    public void downloadDisplayLink(MaterialButton downloadBtn) {
        downloadBtn.setEnabled(false);
        downloadBtn.setText(R.string.downloading_displaylink);
        String url = Pref.getDisplaylinkApkUrl();
        new Thread(() -> {
            try {
                String err = ApkImporter.downloadAndImport(this, url, hundredths ->
                    runOnUiThread(() -> downloadBtn.setText(String.format("%.2f MB", hundredths / 100.0))));
                runOnUiThread(() -> {
                    downloadBtn.setEnabled(true);
                    downloadBtn.setText(R.string.auto_import_displaylink_libs);
                    if (err == null) {
                        Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show();
                        State.log("DisplayLink libraries downloaded and imported successfully");
                    } else {
                        Toast.makeText(this, getString(R.string.import_failed, err), Toast.LENGTH_LONG).show();
                        State.log("Download import error: " + err);
                    }
                    refresh();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    downloadBtn.setEnabled(true);
                    downloadBtn.setText(R.string.auto_import_displaylink_libs);
                    Toast.makeText(this, getString(R.string.import_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                    State.log("Download exception: " + e.getMessage());
                });
            }
        }).start();
    }

    public void importApk() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.setType("application/vnd.android.package-archive");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(pick, REQUEST_IMPORT_APK);
    }
}
