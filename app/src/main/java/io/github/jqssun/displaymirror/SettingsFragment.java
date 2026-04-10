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
import android.widget.TextView;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.jqssun.displaymirror.job.AcquireShizuku;
import io.github.jqssun.displaymirror.shizuku.PermissionManager;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

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
        _updateOverlayStatus(view);
    }

    private void _initSettings(View view) {
        MaterialSwitch autoRotateCheckbox = view.findViewById(R.id.autoRotateCheckbox);
        MaterialSwitch autoScaleCheckbox = view.findViewById(R.id.autoScaleCheckbox);
        MaterialSwitch autoScreenOffCheckbox = view.findViewById(R.id.autoScreenOffCheckbox);
        MaterialSwitch useBlackImageCheckbox = view.findViewById(R.id.useBlackImageCheckbox);
        MaterialSwitch singleAppModeCheckbox = view.findViewById(R.id.singleAppModeCheckbox);
        MaterialButton selectAppButton = view.findViewById(R.id.selectAppButton);
        View singleAppContainer = view.findViewById(R.id.singleAppContainer);
        MaterialSwitch autoBindInputCheckbox = view.findViewById(R.id.autoBindInputCheckbox);
        MaterialSwitch autoMoveImeCheckbox = view.findViewById(R.id.autoMoveImeCheckbox);
        TextView dpiValueText = view.findViewById(R.id.dpiValueText);
        View dpiRow = view.findViewById(R.id.dpiRow);
        MaterialSwitch disableUsbAudioCheckbox = view.findViewById(R.id.disableUsbAudioCheckbox);

        autoRotateCheckbox.setChecked(Pref.getAutoRotate());
        autoScaleCheckbox.setChecked(Pref.getAutoScale());
        autoScreenOffCheckbox.setChecked(Pref.getAutoScreenOff());
        useBlackImageCheckbox.setChecked(Pref.getUseBlackImage());
        disableUsbAudioCheckbox.setChecked(Pref.getDisableUsbAudio());

        boolean singleAppMode = Pref.getSingleAppMode();
        singleAppModeCheckbox.setChecked(singleAppMode);
        autoBindInputCheckbox.setChecked(Pref.getAutoBindInput());
        autoMoveImeCheckbox.setChecked(Pref.getAutoMoveIme());
        dpiValueText.setText(getString(R.string.dpi_text_size_value, Pref.getSingleAppDpi()));

        if (ShizukuUtils.hasPermission()) {
            TextView autoScreenOffDesc = view.findViewById(R.id.autoScreenOffDesc);
            autoScreenOffDesc.setText(R.string.auto_screen_off_with_warning_desc);
        }

        String selectedAppName = preferences.getString(Pref.KEY_SELECTED_APP_NAME, "");
        TextView singleAppTitle = view.findViewById(R.id.singleAppTitle);
        if (!selectedAppName.isEmpty() && singleAppMode) {
            singleAppTitle.setText(getString(R.string.single_app_projection_with_name, selectedAppName));
        }

        boolean hasShizuku = ShizukuUtils.hasPermission();
        singleAppModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_SINGLE_APP_MODE, isChecked).apply();
            autoScaleCheckbox.setEnabled(!isChecked);
            _setSingleAppChildrenEnabled(singleAppContainer, isChecked, hasShizuku);
            if (!isChecked) {
                singleAppTitle.setText(R.string.single_app_projection);
            } else if (!selectedAppName.isEmpty()) {
                singleAppTitle.setText(getString(R.string.single_app_projection_with_name, selectedAppName));
            }
        });
        autoScaleCheckbox.setEnabled(!singleAppMode);
        _setSingleAppChildrenEnabled(singleAppContainer, singleAppMode, hasShizuku);

        autoRotateCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_ROTATE, c).apply());
        autoScaleCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_SCALE, c).apply());
        autoScreenOffCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_SCREEN_OFF, c).apply());
        useBlackImageCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_USE_BLACK_IMAGE, c).apply());
        autoBindInputCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_BIND_INPUT, c).apply());
        autoMoveImeCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_MOVE_IME, c).apply());

        selectAppButton.setOnClickListener(v -> _showAppSelectionDialog());
        dpiRow.setOnClickListener(v -> _showDpiDialog(dpiValueText));

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

        // Permissions
        MaterialButton shizukuPermissionBtn = view.findViewById(R.id.shizukuPermissionBtn);
        shizukuPermissionBtn.setOnClickListener(v -> State.startNewJob(new AcquireShizuku()));

        _updateShizukuStatus(view);
        _updateOverlayStatus(view);

        // About
        TextView versionText = view.findViewById(R.id.versionText);
        try {
            String ver = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            versionText.setText(getString(R.string.version_format, ver, android.os.Build.VERSION.RELEASE));
        } catch (Exception e) {
            versionText.setText(R.string.version_unknown);
        }
        view.findViewById(R.id.websiteLink).setOnClickListener(v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jqssun/android-screen-mirror"))));

        view.findViewById(R.id.shizukuBtn).setOnClickListener(v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rikkaapps/shizuku"))));

        view.findViewById(R.id.exitBtn).setOnClickListener(v -> {
            io.github.jqssun.displaymirror.job.AutoRotateAndScaleForDisplaylink.instance = null;
            io.github.jqssun.displaymirror.job.ExitAll.execute(requireActivity(), false);
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

    private void _showAppSelectionDialog() {
        PackageManager pm = requireContext().getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(intent, 0);
        launcherApps.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));

        ArrayAdapter<ResolveInfo> adapter = new ArrayAdapter<ResolveInfo>(
                requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1, launcherApps) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
                }
                ResolveInfo app = getItem(position);
                if (app != null) {
                    ((TextView) convertView.findViewById(android.R.id.text1)).setText(app.loadLabel(pm));
                    ((TextView) convertView.findViewById(android.R.id.text2)).setText(app.activityInfo.packageName);
                }
                return convertView;
            }
        };

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
                    ((TextView) view.findViewById(R.id.singleAppTitle))
                        .setText(getString(R.string.single_app_projection_with_name, name));
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

    private void _setSingleAppChildrenEnabled(View container, boolean singleAppOn, boolean hasShizuku) {
        boolean enabled = singleAppOn && hasShizuku;
        if (container instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) container;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                child.setEnabled(enabled);
                if (child instanceof ViewGroup) _setAllEnabled((ViewGroup) child, enabled);
            }
        }
    }

    private void _setAllEnabled(ViewGroup vg, boolean enabled) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            child.setEnabled(enabled);
            if (child instanceof ViewGroup) _setAllEnabled((ViewGroup) child, enabled);
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

}
