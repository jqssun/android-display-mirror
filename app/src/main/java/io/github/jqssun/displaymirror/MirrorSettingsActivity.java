package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.github.jqssun.displaymirror.job.AcquireShizuku;
import io.github.jqssun.displaymirror.job.ConnectToClient;
import io.github.jqssun.displaymirror.job.SunshineServer;
import io.github.jqssun.displaymirror.shizuku.PermissionManager;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.List;

public class MirrorSettingsActivity extends AppCompatActivity {
    private SharedPreferences preferences;
    public static final String PREF_NAME = "mirror_settings";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mirror_settings);
        preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.settings_apply_next_launch);
        }
        
        CheckBox singleAppModeCheckbox = findViewById(R.id.singleAppModeCheckbox);
        Button selectAppButton = findViewById(R.id.selectAppButton);
        View singleAppContainer = findViewById(R.id.singleAppContainer);
        CheckBox autoRotateCheckbox = findViewById(R.id.autoRotateCheckbox);
        CheckBox autoScaleCheckbox = findViewById(R.id.autoScaleCheckbox);
        EditText dpiEditText = findViewById(R.id.dpiEditText);
        LinearLayout dpiLayout = findViewById(R.id.dpiLayout);
        CheckBox autoHideFloatingCheckbox = findViewById(R.id.autoHideFloatingCheckbox);
        CheckBox autoScreenOffCheckbox = findViewById(R.id.autoScreenOffCheckbox);
        CheckBox autoBindInputCheckbox = findViewById(R.id.autoBindInputCheckbox);
        CheckBox autoMoveImeCheckbox = findViewById(R.id.autoMoveImeCheckbox);
        CheckBox disableUsbAudioCheckbox = findViewById(R.id.disableUsbAudioCheckbox);
        CheckBox useTouchscreenCheckbox = findViewById(R.id.useTouchscreenCheckbox);
        CheckBox autoMatchAspectRatioCheckbox = findViewById(R.id.autoMatchAspectRatioCheckbox);
        CheckBox showFloatingInMirrorModeCheckbox = findViewById(R.id.showFloatingInMirrorModeCheckbox);
        CheckBox autoConnectClientCheckbox = findViewById(R.id.autoConnectClientCheckbox);
        LinearLayout clientConnectionContainer = findViewById(R.id.clientConnectionContainer);
        Spinner clientSpinner = findViewById(R.id.clientSpinner);
        Button connectClientButton = findViewById(R.id.connectClientButton);
        CheckBox useBlackImageCheckbox = findViewById(R.id.useBlackImageCheckbox);
        CheckBox preventAutoLockCheckbox = findViewById(R.id.preventAutoLockCheckbox);
        CheckBox disableRemoteSubmixCheckbox = findViewById(R.id.disableRemoteSubmixCheckbox);
        
        boolean singleAppMode = Pref.getSingleAppMode();
        boolean autoRotate = Pref.getAutoRotate();
        boolean autoScale = Pref.getAutoScale();
        int singleAppDpi = Pref.getSingleAppDpi();
        boolean floatingBackButton = Pref.getAutoHideFloatingBackButton();
        boolean autoScreenOff = Pref.getAutoScreenOff();
        boolean autoBindInput = Pref.getAutoBindInput();
        boolean autoMoveIme = Pref.getAutoMoveIme();
        boolean disableUsbAudio = Pref.getDisableUsbAudio();
        boolean useTouchscreen = Pref.getUseTouchscreen();
        boolean autoMatchAspectRatio = Pref.getAutoMatchAspectRatio();
        boolean showFloatingInMirrorMode = Pref.getShowFloatingInMirrorMode();
        boolean autoConnectClient = Pref.getAutoConnectClient();
        boolean useBlackImage = Pref.getUseBlackImage();
        boolean preventAutoLock = Pref.getPreventAutoLock();
        boolean disableRemoteSubmix = Pref.getDisableRemoteSubmix();
        
        singleAppModeCheckbox.setChecked(singleAppMode);
        autoRotateCheckbox.setChecked(autoRotate);
        autoScaleCheckbox.setChecked(autoScale);
        dpiEditText.setText(String.valueOf(singleAppDpi));
        autoHideFloatingCheckbox.setChecked(floatingBackButton);
        autoScreenOffCheckbox.setChecked(autoScreenOff);
        autoBindInputCheckbox.setChecked(autoBindInput);
        autoMoveImeCheckbox.setChecked(autoMoveIme);
        disableUsbAudioCheckbox.setChecked(disableUsbAudio);
        useTouchscreenCheckbox.setChecked(useTouchscreen);
        autoMatchAspectRatioCheckbox.setChecked(autoMatchAspectRatio);
        showFloatingInMirrorModeCheckbox.setChecked(showFloatingInMirrorMode);
        autoConnectClientCheckbox.setChecked(autoConnectClient);
        useBlackImageCheckbox.setChecked(useBlackImage);
        preventAutoLockCheckbox.setChecked(preventAutoLock);
        disableRemoteSubmixCheckbox.setChecked(disableRemoteSubmix);
        if (ShizukuUtils.hasPermission()) {
            autoScreenOffCheckbox.setText(R.string.auto_screen_off_with_warning);
        }

        String selectedAppName = preferences.getString(Pref.KEY_SELECTED_APP_NAME, "");
        if (!selectedAppName.isEmpty() && singleAppMode) {
            singleAppModeCheckbox.setText(getString(R.string.single_app_projection_with_name, selectedAppName));
        } else {
            singleAppModeCheckbox.setText(R.string.single_app_projection);
        }
        
        singleAppModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_SINGLE_APP_MODE, isChecked).apply();
            autoScaleCheckbox.setEnabled(!isChecked);
            autoMatchAspectRatioCheckbox.setEnabled(!isChecked);
            showFloatingInMirrorModeCheckbox.setEnabled(!isChecked);
            singleAppContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            
            if (!isChecked) {
                singleAppModeCheckbox.setText(R.string.single_app_projection);
            } else if (!selectedAppName.isEmpty()) {
                singleAppModeCheckbox.setText(getString(R.string.single_app_projection_with_name, selectedAppName));
            }
        });
        autoScaleCheckbox.setEnabled(!singleAppMode);
        autoMatchAspectRatioCheckbox.setEnabled(!singleAppMode);
        showFloatingInMirrorModeCheckbox.setEnabled(!singleAppMode);
        
        autoRotateCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_AUTO_ROTATE, isChecked).apply();
        });

        autoScaleCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_AUTO_SCALE, isChecked).apply();
        });
        autoScaleCheckbox.setEnabled(!singleAppMode);

        Button resolutionButton = findViewById(R.id.resolutionButton);
        TextView currentResolutionText = findViewById(R.id.currentResolutionText);
        
        updateResolutionText(currentResolutionText);
        
        resolutionButton.setOnClickListener(v -> {
            showResolutionDialog(currentResolutionText);
        });

        Button aboutButton = findViewById(R.id.aboutButton);
        aboutButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        });


        Button shizukuPermissionBtn = findViewById(R.id.shizukuPermissionBtn);
        shizukuPermissionBtn.setOnClickListener(v -> {
            State.startNewJob(new AcquireShizuku());
        });

        TextView shizukuStatus = findViewById(R.id.shizukuStatus);
        TextView accessibilityStatus = findViewById(R.id.accessibilityStatus);
        TextView overlayStatus = findViewById(R.id.overlayStatus);
        updateShizukuStatus(shizukuStatus, shizukuPermissionBtn);
        updateAccessibilityStatus(accessibilityStatus);
        updateOverlayStatus(overlayStatus);

        selectAppButton.setOnClickListener(v -> {
            showAppSelectionDialog();
        });

        dpiEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveDpiSetting(dpiEditText);
            }
        });

        Button dpiConfirmButton = findViewById(R.id.dpiConfirmButton);
        dpiConfirmButton.setOnClickListener(v -> {
            saveDpiSetting(dpiEditText);
        });

        autoHideFloatingCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_AUTO_HIDE_FLOATING_BACK_BUTTON, isChecked).apply();
        });

        singleAppContainer.setVisibility(singleAppMode ? View.VISIBLE : View.GONE);

        autoScreenOffCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_AUTO_SCREEN_OFF, isChecked).apply();
        });

        autoBindInputCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_AUTO_BIND_INPUT, isChecked).apply();
        });
        
        if (!ShizukuUtils.hasPermission()) {
            autoBindInputCheckbox.setEnabled(false);
        }

        autoMoveImeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_AUTO_MOVE_IME, isChecked).apply();
        });
        
        if (!ShizukuUtils.hasPermission()) {
            autoMoveImeCheckbox.setEnabled(false);
        }

        disableUsbAudioCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_DISABLE_USB_AUDIO, isChecked).apply();
            
            if (ShizukuUtils.hasPermission()) {
                if (PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
                    try {
                        Settings.Secure.putInt(getContentResolver(),
                                "usb_audio_automatic_routing_disabled", isChecked ? 1 : 0);
                    } catch (SecurityException e) {
                        State.log("failed to set usb_audio_automatic_routing_disabled: " + e);
                    }
                }
            }
        });
        
        if (!ShizukuUtils.hasPermission()) {
            disableUsbAudioCheckbox.setEnabled(false);
        }

        useTouchscreenCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_USE_TOUCHSCREEN, isChecked).apply();
        });
        
        if (!ShizukuUtils.hasPermission()) {
            useTouchscreenCheckbox.setEnabled(false);
        }

        autoMatchAspectRatioCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_AUTO_MATCH_ASPECT_RATIO, isChecked).apply();
        });
        
        if (!ShizukuUtils.hasPermission()) {
            autoMatchAspectRatioCheckbox.setEnabled(false);
        }

        showFloatingInMirrorModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_SHOW_FLOATING_IN_MIRROR_MODE, isChecked).apply();
        });

        clientConnectionContainer.setVisibility(autoConnectClient ? View.VISIBLE : View.GONE);
        
        autoConnectClientCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_AUTO_CONNECT_CLIENT, isChecked).apply();
            clientConnectionContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            
            if (isChecked) {
                loadClientList(clientSpinner);
            }
        });
        
        if (autoConnectClient) {
            loadClientList(clientSpinner);
        }
        
        connectClientButton.setOnClickListener(v -> {
            String selectedClient = (String) clientSpinner.getSelectedItem();
            if (selectedClient != null && !selectedClient.isEmpty()) {
                if (selectedClient.equals(getString(R.string.manual_input))) {
                    showManualInputDialog();
                } else {
                    preferences.edit().putString(Pref.KEY_SELECTED_CLIENT, selectedClient).apply();
                    int pin = (int)(Math.random() * 9000) + 1000;
                    SunshineServer.suppressPin = String.valueOf(pin);
                    ConnectToClient.connect(pin);
                }
            }
        });

        CheckBox disableAccessibilityCheckbox = findViewById(R.id.disableAccessibilityCheckbox);
        boolean disableAccessibility = preferences.getBoolean(Pref.KEY_DISABLE_ACCESSIBILITY, false);
        disableAccessibilityCheckbox.setChecked(disableAccessibility);
        
        disableAccessibilityCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_DISABLE_ACCESSIBILITY, isChecked).apply();
            updateAccessibilityStatus(accessibilityStatus);
            if (isChecked) {
                if (ShizukuUtils.hasPermission()) {
                    TouchpadAccessibilityService.disableAll(MirrorSettingsActivity.this);
                }
            }
        });

        useBlackImageCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_USE_BLACK_IMAGE, isChecked).apply();
        });

        preventAutoLockCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_PREVENT_AUTO_LOCK, isChecked).apply();
        });
        if (!ShizukuUtils.hasPermission()) {
            preventAutoLockCheckbox.setEnabled(false);
        }

        disableRemoteSubmixCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_DISABLE_REMOTE_SUBMIX, isChecked).apply();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SunshineServer.suppressPin = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        TextView shizukuStatus = findViewById(R.id.shizukuStatus);
        Button shizukuPermissionBtn = findViewById(R.id.shizukuPermissionBtn);
        TextView accessibilityStatus = findViewById(R.id.accessibilityStatus);
        TextView overlayStatus = findViewById(R.id.overlayStatus);
        
        updateShizukuStatus(shizukuStatus, shizukuPermissionBtn);
        updateAccessibilityStatus(accessibilityStatus);
        updateOverlayStatus(overlayStatus);

        CheckBox disableAccessibilityCheckbox = findViewById(R.id.disableAccessibilityCheckbox);
        boolean disableAccessibility = preferences.getBoolean(Pref.KEY_DISABLE_ACCESSIBILITY, false);
        disableAccessibilityCheckbox.setChecked(disableAccessibility);
    }

    private void updateShizukuStatus(TextView statusView, Button permissionBtn) {
        boolean started = ShizukuUtils.hasShizukuStarted();
        boolean hasPermission = ShizukuUtils.hasPermission();
        
        String status;
        if (!started) {
            status = getString(R.string.status_not_started);
            permissionBtn.setVisibility(View.GONE);
        } else if (!hasPermission) {
            status = getString(R.string.status_started_not_authorized);
            permissionBtn.setVisibility(View.VISIBLE);
        } else {
            status = getString(R.string.status_authorized);
            permissionBtn.setVisibility(View.GONE);
        }
        
        statusView.setText(status);
    }

    private void updateAccessibilityStatus(TextView statusView) {
        boolean isEnabled = TouchpadAccessibilityService.isAccessibilityServiceEnabled(this);
        boolean isDisabled = preferences.getBoolean(Pref.KEY_DISABLE_ACCESSIBILITY, false);
        
        if (isDisabled) {
            statusView.setText(R.string.status_disabled);
        } else {
            statusView.setText(isEnabled ? getString(R.string.status_authorized) : getString(R.string.status_not_authorized));
        }
        
        View parent = (View) statusView.getParent();
        Button accessibilityPermissionBtn = parent.findViewById(R.id.accessibilityPermissionBtn);

        if (accessibilityPermissionBtn != null) {
            accessibilityPermissionBtn.setVisibility((isEnabled || isDisabled) ? View.GONE : View.VISIBLE);
            accessibilityPermissionBtn.setOnClickListener(v -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            });
        }
    }

    private void updateOverlayStatus(TextView statusView) {
        boolean hasPermission = Settings.canDrawOverlays(this);
        statusView.setText(hasPermission ? getString(R.string.status_authorized) : getString(R.string.status_not_authorized));
        
        View parent = (View) statusView.getParent();
        Button overlayPermissionBtn = parent.findViewById(R.id.overlayPermissionBtn);

        if (overlayPermissionBtn != null) {
            overlayPermissionBtn.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
            overlayPermissionBtn.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            });
        }
    }

    private void showAppSelectionDialog() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(intent, 0);
        
        launcherApps.sort((a, b) -> {
            String labelA = a.loadLabel(pm).toString();
            String labelB = b.loadLabel(pm).toString();
            return labelA.compareToIgnoreCase(labelB);
        });
        
        AppListAdapter adapter = new AppListAdapter(this, launcherApps, pm);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_app_title);
        builder.setAdapter(adapter, (dialog, which) -> {
            ResolveInfo selectedApp = launcherApps.get(which);
            String selectedPackage = selectedApp.activityInfo.packageName;
            String selectedName = selectedApp.loadLabel(pm).toString();
            
            preferences.edit()
                    .putString(Pref.KEY_SELECTED_APP_PACKAGE, selectedPackage)
                    .putString(Pref.KEY_SELECTED_APP_NAME, selectedName)
                    .apply();
            
            CheckBox singleAppModeCheckbox = findViewById(R.id.singleAppModeCheckbox);
            singleAppModeCheckbox.setText(getString(R.string.single_app_projection_with_name, selectedName));
        });
        
        builder.show();
    }

    private static class AppListAdapter extends ArrayAdapter<ResolveInfo> {
        private final PackageManager pm;
        private final int ICON_SIZE_DP = 36;
        
        public AppListAdapter(Context context, List<ResolveInfo> apps, PackageManager pm) {
            super(context, android.R.layout.simple_list_item_2, android.R.id.text1, apps);
            this.pm = pm;
        }
        
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(
                        android.R.layout.simple_list_item_2, parent, false);
            }
            
            ResolveInfo app = getItem(position);
            if (app != null) {
                TextView text1 = convertView.findViewById(android.R.id.text1);
                TextView text2 = convertView.findViewById(android.R.id.text2);
                
                text1.setText(app.loadLabel(pm));
                text2.setText(app.activityInfo.packageName);
                
                try {
                    android.graphics.drawable.Drawable icon = app.loadIcon(pm);
                    float density = getContext().getResources().getDisplayMetrics().density;
                    int iconSizePx = Math.round(ICON_SIZE_DP * density);
                    icon.setBounds(0, 0, iconSizePx, iconSizePx);
                    text1.setCompoundDrawables(icon, null, null, null);
                    text1.setCompoundDrawablePadding(10);
                } catch (Exception e) {
                    // ignore
                }
            }
            
            return convertView;
        }
    }

    private void saveDpiSetting(EditText dpiEditText) {
        try {
            int dpi = Integer.parseInt(dpiEditText.getText().toString());
            // limit dpi to a reasonable range, e.g. 60-600
            if (dpi < 60) dpi = 60;
            if (dpi > 600) dpi = 600;
            dpiEditText.setText(String.valueOf(dpi));
            preferences.edit().putInt(Pref.KEY_SINGLE_APP_DPI, dpi).apply();
        } catch (NumberFormatException e) {
            dpiEditText.setText(Pref.getSingleAppDpi());
        }
    }

    private void updateResolutionText(TextView textView) {
        String resolutionText = getString(R.string.displaylink_output_format,
                Pref.getDisplaylinkWidth(),
                Pref.getDisplaylinkHeight(),
                Pref.getDisplaylinkRefreshRate());
        textView.setText(resolutionText);
    }

    private void showResolutionDialog(TextView currentResolutionText) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_resolution_settings, null);
        EditText widthEditText = dialogView.findViewById(R.id.widthEditText);
        EditText heightEditText = dialogView.findViewById(R.id.heightEditText);
        EditText refreshRateEditText = dialogView.findViewById(R.id.refreshRateEditText);
        Spinner resolutionPresetSpinner = dialogView.findViewById(R.id.resolutionPresetSpinner);
        
        widthEditText.setText(String.valueOf(Pref.getDisplaylinkWidth()));
        heightEditText.setText(String.valueOf(Pref.getDisplaylinkHeight()));
        refreshRateEditText.setText(String.valueOf(Pref.getDisplaylinkRefreshRate()));
        
        String[] resolutionPresets = new String[]{getString(R.string.quick_presets), "1080p", "1440p", "2160p", "Apple iPad"};
        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            resolutionPresets
        );
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionPresetSpinner.setAdapter(resolutionAdapter);
        
        resolutionPresetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    switch (position) {
                        case 1: // 1080p
                            widthEditText.setText("1920");
                            heightEditText.setText("1080");
                            refreshRateEditText.setText("60");
                            break;
                        case 2: // 1440p
                            widthEditText.setText("2560");
                            heightEditText.setText("1440");
                            refreshRateEditText.setText("60");
                            break;
                        case 3: // 2160p
                            widthEditText.setText("3840");
                            heightEditText.setText("2160");
                            refreshRateEditText.setText("60");
                            break;
                        case 4: // iPad
                            widthEditText.setText("2048");
                            heightEditText.setText("1536");
                            refreshRateEditText.setText("60");
                            break;
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.displaylink_resolution_title);
        builder.setView(dialogView);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            try {
                int width = Integer.parseInt(widthEditText.getText().toString());
                int height = Integer.parseInt(heightEditText.getText().toString());
                int refreshRate = Integer.parseInt(refreshRateEditText.getText().toString());
                
                refreshRate = Math.max(24, Math.min(240, refreshRate));
                
                preferences.edit()
                        .putInt(Pref.KEY_DISPLAYLINK_WIDTH, width)
                        .putInt(Pref.KEY_DISPLAYLINK_HEIGHT, height)
                        .putInt(Pref.KEY_DISPLAYLINK_REFRESH_RATE, refreshRate)
                        .apply();
                
                updateResolutionText(currentResolutionText);
            } catch (NumberFormatException e) {
                // ignore
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void loadClientList(Spinner spinner) {
        String selectedClient = Pref.getSelectedClient();
        List<String> clients = new ArrayList<>();
        clients.add(getString(R.string.manual_input));
        if (!selectedClient.isEmpty()) {
            clients.add(selectedClient);
        }
        clients.addAll(State.discoveredConnectScreenClients);
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            clients
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        if (!selectedClient.isEmpty()) {
            for (int i = 0; i < clients.size(); i++) {
                if (clients.get(i).equals(selectedClient)) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }
    }

    private void showManualInputDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manual_client_input, null);
        EditText ipEditText = dialogView.findViewById(R.id.ipEditText);
        EditText portEditText = dialogView.findViewById(R.id.portEditText);
        
        portEditText.setText("42515");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.manual_input_client_title);
        builder.setView(dialogView);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            try {
                String ip = ipEditText.getText().toString().trim();
                String port = portEditText.getText().toString().trim();
                
                if (!ip.isEmpty()) {
                    String clientAddress = ip;
                    if (!port.isEmpty()) {
                        clientAddress += ":" + port;
                    }
                    preferences.edit().putString(Pref.KEY_SELECTED_CLIENT, clientAddress).apply();
                    
                    Spinner clientSpinner = findViewById(R.id.clientSpinner);
                    loadClientList(clientSpinner);
                    int pin = (int) (Math.random() * 9000) + 1000;
                    SunshineServer.suppressPin = String.valueOf(pin);
                    ConnectToClient.connect(pin);
                }
            } catch (Exception e) {
                // ignore
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
} 