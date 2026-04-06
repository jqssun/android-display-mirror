package io.github.jqssun.displaymirror;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.jqssun.displaymirror.job.AcquireShizuku;
import io.github.jqssun.displaymirror.job.AutoRotateAndScaleForDisplaylink;
import io.github.jqssun.displaymirror.job.CreateVirtualDisplay;
import io.github.jqssun.displaymirror.job.ExitAll;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.ref.WeakReference;

import rikka.shizuku.Shizuku;
import com.topjohnwu.superuser.Shell;

public class MirrorMainActivity extends AppCompatActivity implements IMainActivity {

    static {
        // Set settings before the main shell can be created
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10));
    }

    public static final int REQUEST_CODE_MEDIA_PROJECTION = 1001;
    public static final int REQUEST_RECORD_AUDIO_PERMISSION = 1002;
    public static final String TAG = "MirrorMainActivity";

    private RecyclerView logRecyclerView;
    private LogAdapter logAdapter;

    private static final long MONITOR_INIT_DELAY = 3000;
    private long lastCheckTime = 0;

    Button settingsBtn;
    Button screenOffBtn;
    Button touchScreenBtn;
    Button exitBtn;
    TextView mirrorStatus;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            State.resumeJob();
        } else {
            State.log("Unknown permission request code: " + requestCode);
        }
    }

    private void onRequestShizukuPermissionsResult(int requestCode, int grantResult) {
        if (requestCode == AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE) {
            State.log("Shizuku permission result: " + (grantResult == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
            State.resumeJob();
        } else {
            State.log("Unknown Shizuku request code: " + requestCode);
        }
    }

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER =
        this::onRequestShizukuPermissionsResult;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
                android.util.Log.i("MainActivity", "Successfully added hidden API exemption");
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

        AcquireShizuku.fixRootShizuku();
        if (!Pref.getDisableAccessibility()) {
            ensureAccessiblityServiceStarted();
        }

        if (SunshineService.instance == null) {
            startMediaProjectionService();
        } else {
            State.log("SunshineService already running");
        }
        
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_main);

        logRecyclerView = findViewById(R.id.logRecyclerView);
        logAdapter = new LogAdapter(State.logs);
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logAdapter);


        State.uiState.observe(this, this::updateUI);
        initHomeControls();
    }

    private void ensureAccessiblityServiceStarted() {
        if (TouchpadAccessibilityService.isAccessibilityServiceEnabled(this)) {
            Intent serviceIntent = new Intent(this, TouchpadAccessibilityService.class);
            this.startService(serviceIntent);
        }
    }

    private void initHomeControls() {
        settingsBtn = findViewById(R.id.settingsBtn);
        screenOffBtn = findViewById(R.id.screenOffBtn);
        touchScreenBtn = findViewById(R.id.touchScreenBtn);
        exitBtn = findViewById(R.id.exitBtn);
        mirrorStatus = findViewById(R.id.mirrorStatus);
        
        refresh();

        settingsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, MirrorSettingsActivity.class);
            startActivity(intent);
        });

        screenOffBtn.setOnClickListener(v -> {
            CreateVirtualDisplay.doPowerOffScreen(this);
        });

        touchScreenBtn.setOnClickListener(v -> {
            boolean useTouchscreen = Pref.getUseTouchscreen();
            if (ShizukuUtils.hasPermission() && useTouchscreen) {
                VirtualDisplay virtualDisplay = State.displaylinkState.getVirtualDisplay();
                if (virtualDisplay == null) {
                    virtualDisplay = State.mirrorVirtualDisplay;
                }
                if (virtualDisplay == null) {
                    return;
                }
                int displayId = virtualDisplay.getDisplay().getDisplayId();
                Intent intent = new Intent(this, TouchscreenActivity.class);
                intent.putExtra("surface", virtualDisplay.getSurface());
                intent.putExtra("display", displayId);
                startActivity(intent);
            } else {
                TouchpadActivity.startTouchpad(this, State.lastSingleAppDisplay, false);
            }
        });

        exitBtn.setOnClickListener(v -> {
            if (AutoRotateAndScaleForDisplaylink.instance != null) {
                AutoRotateAndScaleForDisplaylink.instance.release();
            }
            ExitAll.execute(this, false);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        State.setCurrentActivity(this);
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        State.setCurrentActivity(null);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
       
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
                    State.setMediaProjection(mediaProjectionManager.getMediaProjection(RESULT_OK, data));
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

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    public void updateLogs() {
        try {
            if (logAdapter != null) {
                logAdapter.notifyDataSetChanged();
                logRecyclerView.scrollToPosition(logAdapter.getItemCount() - 1);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public void startMediaProjectionService() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) this.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            Intent captureIntent = null;
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

    private void updateUI(MirrorUiState state) {
        if (state.errorStatusText != null) {
            mirrorStatus.setText(state.errorStatusText);
            settingsBtn.setVisibility(View.VISIBLE);
            screenOffBtn.setVisibility(View.GONE);
            touchScreenBtn.setVisibility(View.GONE);
            return;
        }
        mirrorStatus.setText(state.mirrorStatusText);
        mirrorStatus.setVisibility(View.VISIBLE);
        
        settingsBtn.setVisibility(state.settingsBtnVisibility ? View.VISIBLE : View.GONE);
        screenOffBtn.setText(R.string.screen_off);
        screenOffBtn.setVisibility(state.screenOffBtnVisibility ? View.VISIBLE : View.GONE);
        touchScreenBtn.setVisibility(state.touchScreenBtnVisibility ? View.VISIBLE : View.GONE);
        
        if (state.touchScreenBtnVisibility) {
            touchScreenBtn.setText(state.touchScreenBtnText);
        }
    }

    public void refresh() {
        if (State.uiState.getValue().errorStatusText != null) {
            return;
        }
        boolean singleAppMode = Pref.getSingleAppMode();
        boolean useTouchscreen = Pref.getUseTouchscreen();
        
        boolean isScreenMirroring = State.mirrorVirtualDisplay != null || 
                                   State.displaylinkState.getVirtualDisplay() != null || 
                                   State.lastSingleAppDisplay != 0;

        MirrorUiState newUiState = new MirrorUiState();

        if (SunshineService.instance == null) {
            newUiState.mirrorStatusText = getString(R.string.status_no_projection_permission);
            newUiState.settingsBtnVisibility = true;
            newUiState.screenOffBtnVisibility = false;
            newUiState.touchScreenBtnVisibility = false;
        } else if (isScreenMirroring) {
            newUiState.mirrorStatusText = getString(R.string.status_projecting);
            newUiState.settingsBtnVisibility = false;
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
            newUiState.settingsBtnVisibility = true;
            newUiState.screenOffBtnVisibility = false;
            newUiState.touchScreenBtnVisibility = false;
        }

        State.uiState.setValue(newUiState);
    }
}