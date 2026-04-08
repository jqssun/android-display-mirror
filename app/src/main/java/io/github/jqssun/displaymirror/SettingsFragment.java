package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.jqssun.displaymirror.job.AcquireShizuku;
import io.github.jqssun.displaymirror.job.ConnectToClient;
import io.github.jqssun.displaymirror.job.SunshineServer;
import io.github.jqssun.displaymirror.shizuku.PermissionManager;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends Fragment {
    private SharedPreferences preferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferences = requireContext().getSharedPreferences(Pref.PREF_NAME, Context.MODE_PRIVATE);
        _initSettings(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        View view = getView();
        if (view == null) return;

        _updateShizukuStatus(view);
        _updateAccessibilityStatus(view);
        _updateOverlayStatus(view);

        MaterialSwitch useAccessibilityCheckbox = view.findViewById(R.id.useAccessibilityCheckbox);
        useAccessibilityCheckbox.setChecked(!preferences.getBoolean(Pref.KEY_DISABLE_ACCESSIBILITY, false));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        SunshineServer.suppressPin = null;
    }

    private void _initSettings(View view) {
        MaterialSwitch singleAppModeCheckbox = view.findViewById(R.id.singleAppModeCheckbox);
        MaterialButton selectAppButton = view.findViewById(R.id.selectAppButton);
        View singleAppContainer = view.findViewById(R.id.singleAppContainer);
        MaterialSwitch autoRotateCheckbox = view.findViewById(R.id.autoRotateCheckbox);
        MaterialSwitch autoScaleCheckbox = view.findViewById(R.id.autoScaleCheckbox);
        TextView dpiValueText = view.findViewById(R.id.dpiValueText);
        View dpiRow = view.findViewById(R.id.dpiRow);
        MaterialSwitch autoHideFloatingCheckbox = view.findViewById(R.id.autoHideFloatingCheckbox);
        MaterialSwitch autoScreenOffCheckbox = view.findViewById(R.id.autoScreenOffCheckbox);
        MaterialSwitch autoBindInputCheckbox = view.findViewById(R.id.autoBindInputCheckbox);
        MaterialSwitch autoMoveImeCheckbox = view.findViewById(R.id.autoMoveImeCheckbox);
        MaterialSwitch disableUsbAudioCheckbox = view.findViewById(R.id.disableUsbAudioCheckbox);
        MaterialSwitch useTouchscreenCheckbox = view.findViewById(R.id.useTouchscreenCheckbox);
        MaterialSwitch autoMatchAspectRatioCheckbox = view.findViewById(R.id.autoMatchAspectRatioCheckbox);
        MaterialSwitch showFloatingInMirrorModeCheckbox = view.findViewById(R.id.showFloatingInMirrorModeCheckbox);
        MaterialSwitch showMoonlightCursorCheckbox = view.findViewById(R.id.showMoonlightCursorCheckbox);
        MaterialSwitch autoConnectClientCheckbox = view.findViewById(R.id.autoConnectClientCheckbox);
        LinearLayout clientConnectionContainer = view.findViewById(R.id.clientConnectionContainer);
        Spinner clientSpinner = view.findViewById(R.id.clientSpinner);
        MaterialButton connectClientButton = view.findViewById(R.id.connectClientButton);
        MaterialSwitch useBlackImageCheckbox = view.findViewById(R.id.useBlackImageCheckbox);
        MaterialSwitch preventAutoLockCheckbox = view.findViewById(R.id.preventAutoLockCheckbox);
        MaterialSwitch disableRemoteSubmixCheckbox = view.findViewById(R.id.disableRemoteSubmixCheckbox);

        boolean singleAppMode = Pref.getSingleAppMode();
        singleAppModeCheckbox.setChecked(singleAppMode);
        autoRotateCheckbox.setChecked(Pref.getAutoRotate());
        autoScaleCheckbox.setChecked(Pref.getAutoScale());
        dpiValueText.setText(getString(R.string.dpi_text_size_value, Pref.getSingleAppDpi()));
        autoHideFloatingCheckbox.setChecked(Pref.getAutoHideFloatingBackButton());
        autoScreenOffCheckbox.setChecked(Pref.getAutoScreenOff());
        autoBindInputCheckbox.setChecked(Pref.getAutoBindInput());
        autoMoveImeCheckbox.setChecked(Pref.getAutoMoveIme());
        disableUsbAudioCheckbox.setChecked(Pref.getDisableUsbAudio());
        useTouchscreenCheckbox.setChecked(Pref.getUseTouchscreen());
        autoMatchAspectRatioCheckbox.setChecked(Pref.getAutoMatchAspectRatio());
        showFloatingInMirrorModeCheckbox.setChecked(Pref.getShowFloatingInMirrorMode());
        showMoonlightCursorCheckbox.setChecked(Pref.getShowMoonlightCursor());
        boolean autoConnectClient = Pref.getAutoConnectClient();
        autoConnectClientCheckbox.setChecked(autoConnectClient);
        useBlackImageCheckbox.setChecked(Pref.getUseBlackImage());
        preventAutoLockCheckbox.setChecked(Pref.getPreventAutoLock());
        disableRemoteSubmixCheckbox.setChecked(Pref.getDisableRemoteSubmix());

        if (ShizukuUtils.hasPermission()) {
            TextView autoScreenOffDesc = view.findViewById(R.id.autoScreenOffDesc);
            autoScreenOffDesc.setText(R.string.auto_screen_off_with_warning_desc);
        }

        String selectedAppName = preferences.getString(Pref.KEY_SELECTED_APP_NAME, "");
        if (!selectedAppName.isEmpty() && singleAppMode) {
            TextView singleAppTitle = view.findViewById(R.id.singleAppTitle);
            singleAppTitle.setText(getString(R.string.single_app_projection_with_name, selectedAppName));
        }

        TextView singleAppTitle = view.findViewById(R.id.singleAppTitle);
        boolean hasShizuku = ShizukuUtils.hasPermission();
        singleAppModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_SINGLE_APP_MODE, isChecked).apply();
            autoScaleCheckbox.setEnabled(!isChecked);
            autoMatchAspectRatioCheckbox.setEnabled(!isChecked && hasShizuku);
            showFloatingInMirrorModeCheckbox.setEnabled(!isChecked);
            _setSingleAppChildrenEnabled(singleAppContainer, isChecked, hasShizuku);
            if (!isChecked) {
                singleAppTitle.setText(R.string.single_app_projection);
            } else if (!selectedAppName.isEmpty()) {
                singleAppTitle.setText(getString(R.string.single_app_projection_with_name, selectedAppName));
            }
        });
        autoScaleCheckbox.setEnabled(!singleAppMode);
        autoMatchAspectRatioCheckbox.setEnabled(!singleAppMode && hasShizuku);
        showFloatingInMirrorModeCheckbox.setEnabled(!singleAppMode);
        _setSingleAppChildrenEnabled(singleAppContainer, singleAppMode, hasShizuku);

        autoRotateCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_ROTATE, c).apply());
        autoScaleCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_SCALE, c).apply());
        autoHideFloatingCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_HIDE_FLOATING_BACK_BUTTON, c).apply());
        autoScreenOffCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_SCREEN_OFF, c).apply());

        autoBindInputCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_BIND_INPUT, c).apply());
        autoMoveImeCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_MOVE_IME, c).apply());

        disableUsbAudioCheckbox.setOnCheckedChangeListener((b, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_DISABLE_USB_AUDIO, isChecked).apply();
            if (ShizukuUtils.hasPermission()) {
                if (PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
                    try {
                        Settings.Secure.putInt(requireContext().getContentResolver(),
                                "usb_audio_automatic_routing_disabled", isChecked ? 1 : 0);
                    } catch (SecurityException e) {
                        State.log("failed to set usb_audio_automatic_routing_disabled: " + e);
                    }
                }
            }
        });
        if (!ShizukuUtils.hasPermission()) disableUsbAudioCheckbox.setEnabled(false);

        useTouchscreenCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_USE_TOUCHSCREEN, c).apply());

        autoMatchAspectRatioCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_MATCH_ASPECT_RATIO, c).apply());
        if (!ShizukuUtils.hasPermission()) autoMatchAspectRatioCheckbox.setEnabled(false);

        showFloatingInMirrorModeCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_SHOW_FLOATING_IN_MIRROR_MODE, c).apply());
        useBlackImageCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_USE_BLACK_IMAGE, c).apply());

        preventAutoLockCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_PREVENT_AUTO_LOCK, c).apply());
        if (!ShizukuUtils.hasPermission()) preventAutoLockCheckbox.setEnabled(false);

        disableRemoteSubmixCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_DISABLE_REMOTE_SUBMIX, c).apply());

        selectAppButton.setOnClickListener(v -> _showAppSelectionDialog());

        dpiRow.setOnClickListener(v -> _showDpiDialog(dpiValueText));

        // Permissions
        MaterialButton shizukuPermissionBtn = view.findViewById(R.id.shizukuPermissionBtn);
        shizukuPermissionBtn.setOnClickListener(v -> State.startNewJob(new AcquireShizuku()));

        _updateShizukuStatus(view);
        _updateAccessibilityStatus(view);
        _updateOverlayStatus(view);

        MaterialSwitch useAccessibilityCheckbox = view.findViewById(R.id.useAccessibilityCheckbox);
        useAccessibilityCheckbox.setChecked(!preferences.getBoolean(Pref.KEY_DISABLE_ACCESSIBILITY, false));
        useAccessibilityCheckbox.setOnCheckedChangeListener((b, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_DISABLE_ACCESSIBILITY, !isChecked).apply();
            _updateAccessibilityStatus(view);
            if (!isChecked && ShizukuUtils.hasPermission()) {
                TouchpadAccessibilityService.disableAll(requireContext());
            }
        });

        // Moonlight cursor
        showMoonlightCursorCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_SHOW_MOONLIGHT_CURSOR, c).apply());

        // Moonlight client
        clientConnectionContainer.setVisibility(autoConnectClient ? View.VISIBLE : View.GONE);
        autoConnectClientCheckbox.setOnCheckedChangeListener((b, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_AUTO_CONNECT_CLIENT, isChecked).apply();
            clientConnectionContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) _loadClientList(clientSpinner);
        });
        if (autoConnectClient) _loadClientList(clientSpinner);

        connectClientButton.setOnClickListener(v -> {
            String selectedClient = (String) clientSpinner.getSelectedItem();
            if (selectedClient != null && !selectedClient.isEmpty()) {
                if (selectedClient.equals(getString(R.string.manual_input))) {
                    _showManualInputDialog();
                } else {
                    preferences.edit().putString(Pref.KEY_SELECTED_CLIENT, selectedClient).apply();
                    int pin = (int)(Math.random() * 9000) + 1000;
                    SunshineServer.suppressPin = String.valueOf(pin);
                    ConnectToClient.connect(pin);
                }
            }
        });

    }

    private void _updateShizukuStatus(View view) {
        TextView statusView = view.findViewById(R.id.shizukuStatus);
        MaterialButton permissionBtn = view.findViewById(R.id.shizukuPermissionBtn);
        boolean started = ShizukuUtils.hasShizukuStarted();
        boolean hasPermission = ShizukuUtils.hasPermission();
        if (!started) {
            statusView.setText(R.string.status_not_started);
            permissionBtn.setVisibility(View.GONE);
        } else if (!hasPermission) {
            statusView.setText(R.string.status_started_not_authorized);
            permissionBtn.setVisibility(View.VISIBLE);
        } else {
            statusView.setText(R.string.status_authorized);
            permissionBtn.setVisibility(View.GONE);
        }
    }

    private void _updateAccessibilityStatus(View view) {
        View accessibilityRow = view.findViewById(R.id.accessibilityRow);
        TextView statusView = view.findViewById(R.id.accessibilityStatus);
        MaterialButton permissionBtn = view.findViewById(R.id.accessibilityPermissionBtn);
        boolean useAccessibility = !preferences.getBoolean(Pref.KEY_DISABLE_ACCESSIBILITY, false);
        if (!useAccessibility) {
            accessibilityRow.setVisibility(View.GONE);
            return;
        }
        boolean isEnabled = TouchpadAccessibilityService.isAccessibilityServiceEnabled(requireContext());
        accessibilityRow.setVisibility(View.VISIBLE);
        if (isEnabled) {
            statusView.setText(R.string.status_authorized);
            permissionBtn.setVisibility(View.GONE);
        } else {
            statusView.setText(R.string.status_not_authorized);
            permissionBtn.setVisibility(View.VISIBLE);
            permissionBtn.setOnClickListener(v ->
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }
    }

    private void _updateOverlayStatus(View view) {
        TextView statusView = view.findViewById(R.id.overlayStatus);
        MaterialButton permissionBtn = view.findViewById(R.id.overlayPermissionBtn);
        boolean hasPermission = Settings.canDrawOverlays(requireContext());
        statusView.setText(hasPermission ? R.string.status_authorized : R.string.status_not_authorized);
        if (hasPermission) {
            permissionBtn.setVisibility(View.GONE);
        } else {
            permissionBtn.setVisibility(View.VISIBLE);
            permissionBtn.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + requireContext().getPackageName()))));
        }
    }

    private void _showAppSelectionDialog() {
        PackageManager pm = requireContext().getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(intent, 0);
        launcherApps.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));

        _AppListAdapter adapter = new _AppListAdapter(requireContext(), launcherApps, pm);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_app_title)
            .setAdapter(adapter, (dialog, which) -> {
                ResolveInfo selectedApp = launcherApps.get(which);
                String pkg = selectedApp.activityInfo.packageName;
                String name = selectedApp.loadLabel(pm).toString();
                preferences.edit()
                    .putString(Pref.KEY_SELECTED_APP_PACKAGE, pkg)
                    .putString(Pref.KEY_SELECTED_APP_NAME, name)
                    .apply();
                View view = getView();
                if (view != null) {
                    TextView titleView = view.findViewById(R.id.singleAppTitle);
                    titleView.setText(getString(R.string.single_app_projection_with_name, name));
                }
            })
            .show();
    }

    private void _showDpiDialog(TextView dpiValueText) {
        EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(Pref.getSingleAppDpi()));
        input.setSelectAllOnFocus(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        container.setPadding(pad, pad, pad, 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dpi_text_size)
            .setView(container)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                try {
                    int dpi = Integer.parseInt(input.getText().toString());
                    if (dpi < 60) dpi = 60;
                    if (dpi > 600) dpi = 600;
                    preferences.edit().putInt(Pref.KEY_SINGLE_APP_DPI, dpi).apply();
                    dpiValueText.setText(getString(R.string.dpi_text_size_value, dpi));
                } catch (NumberFormatException ignored) {
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void _loadClientList(Spinner spinner) {
        String selectedClient = Pref.getSelectedClient();
        List<String> clients = new ArrayList<>();
        clients.add(getString(R.string.manual_input));
        if (!selectedClient.isEmpty()) clients.add(selectedClient);
        clients.addAll(State.discoveredMirrorClients);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, clients);
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

    private void _showManualInputDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_manual_client_input, null);
        EditText ipEditText = dialogView.findViewById(R.id.ipEditText);
        EditText portEditText = dialogView.findViewById(R.id.portEditText);
        portEditText.setText("42515");

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.manual_input_client_title)
            .setView(dialogView)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                String ip = ipEditText.getText().toString().trim();
                String port = portEditText.getText().toString().trim();
                if (!ip.isEmpty()) {
                    String addr = port.isEmpty() ? ip : ip + ":" + port;
                    preferences.edit().putString(Pref.KEY_SELECTED_CLIENT, addr).apply();
                    View view = getView();
                    if (view != null) _loadClientList(view.findViewById(R.id.clientSpinner));
                    int pin = (int)(Math.random() * 9000) + 1000;
                    SunshineServer.suppressPin = String.valueOf(pin);
                    ConnectToClient.connect(pin);
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void _setSingleAppChildrenEnabled(View container, boolean singleAppOn, boolean hasShizuku) {
        boolean enabled = singleAppOn && hasShizuku;
        if (container instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) container;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                child.setEnabled(enabled);
                if (child instanceof ViewGroup) {
                    _setAllEnabled((ViewGroup) child, enabled);
                }
            }
        }
    }

    private void _setAllEnabled(ViewGroup vg, boolean enabled) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            child.setEnabled(enabled);
            if (child instanceof ViewGroup) {
                _setAllEnabled((ViewGroup) child, enabled);
            }
        }
    }

    private static class _AppListAdapter extends ArrayAdapter<ResolveInfo> {
        private final PackageManager pm;

        _AppListAdapter(Context context, java.util.List<ResolveInfo> apps, PackageManager pm) {
            super(context, android.R.layout.simple_list_item_2, android.R.id.text1, apps);
            this.pm = pm;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
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
                    int sz = Math.round(36 * density);
                    icon.setBounds(0, 0, sz, sz);
                    text1.setCompoundDrawables(icon, null, null, null);
                    text1.setCompoundDrawablePadding(10);
                } catch (Exception e) { /* ignore */ }
            }
            return convertView;
        }
    }
}
