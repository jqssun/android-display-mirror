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

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import io.github.jqssun.displaymirror.job.AcquireShizuku;

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

    public static final int REQUEST_RECORD_AUDIO_PERMISSION = 1002;
    public static final String TAG = "MirrorMainActivity";

    private long lastCheckTime = 0;

    private final ActivityResultLauncher<Intent> mediaProjectionLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                State.log("User granted screen projection permission");
                lastCheckTime = System.currentTimeMillis();
                if (SunshineService.instance == null) {
                    Intent svc = new Intent(this, SunshineService.class);
                    svc.putExtra("data", data);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
                    else startService(svc);
                    State.log("Starting SunshineService");
                } else {
                    MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    if (mpm == null) return;
                    State.setMediaProjection(mpm.getMediaProjection(RESULT_OK, data));
                    if (State.getMediaProjection() == null) { State.resumeJob(); return; }
                    State.getMediaProjection().registerCallback(new MediaProjection.Callback() {
                        @Override public void onStop() { super.onStop(); State.log("MediaProjection onStop callback"); }
                    }, null);
                    State.resumeJob();
                }
            } else {
                State.log("User denied screen projection permission");
                refresh();
                State.resumeJob();
            }
        });

    private final ActivityResultLauncher<Intent> airplayProjectionLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Intent svc = new Intent(this, AirPlayForegroundService.class);
                svc.putExtra("data", result.getData());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
                else startService(svc);
            }
        });

    private final ActivityResultLauncher<Intent> importApkLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
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
        });

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
        EdgeToEdge.enable(this);
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

        // Setup navigation
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        AppBarConfiguration appBarConfig = new AppBarConfiguration.Builder(
                R.id.overview_fragment, R.id.logs_fragment, R.id.settings_fragment).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig);
        NavigationUI.setupWithNavController(bottomNav, navController);

        State.uiState.observe(this, state -> {});
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navHostFragment.getNavController(),
                new AppBarConfiguration.Builder(
                    R.id.overview_fragment, R.id.logs_fragment, R.id.settings_fragment).build())
                || super.onSupportNavigateUp();
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


    public void startMirroring() {
        AcquireShizuku.notifyIfUidDropped();
        if (SunshineService.instance == null) {
            startMediaProjectionService();
        } else {
            State.log("SunshineService already running");
            refresh();
        }
    }

    public void requestAirPlayProjection() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) {
            Intent captureIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                captureIntent = mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            } else {
                captureIntent = mpm.createScreenCaptureIntent();
            }
            airplayProjectionLauncher.launch(captureIntent);
        }
    }

    public void startMediaProjectionService() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) {
            Intent captureIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                captureIntent = mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            } else {
                captureIntent = mpm.createScreenCaptureIntent();
            }
            mediaProjectionLauncher.launch(captureIntent);
        } else {
            throw new RuntimeException("Failed to get MediaProjectionManager service");
        }
    }

    public void refresh() {
        MirrorUiState current = State.uiState.getValue();
        if (current != null && current.errorStatusText != null) {
            return;
        }
        boolean isScreenMirroring = State.mirrorVirtualDisplay != null ||
                                   State.displaylinkState.getVirtualDisplay() != null ||
                                   State.lastSingleAppDisplay != 0;

        MirrorUiState newUiState = new MirrorUiState();

        if (SunshineService.instance == null) {
            newUiState.mirrorStatusText = getString(R.string.status_idle);
            newUiState.startBtnVisibility = true;
        } else if (isScreenMirroring) {
            newUiState.mirrorStatusText = getString(R.string.status_casting);
            newUiState.stopBtnVisibility = true;
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
        importApkLauncher.launch(pick);
    }
}
