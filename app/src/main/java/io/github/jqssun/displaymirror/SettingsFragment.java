package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.VirtualDisplay;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

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
        _updateLiveControls(view);
    }

    private void _initSettings(View view) {
        MaterialSwitch trustedDisplayCheckbox = view.findViewById(R.id.trustedDisplayCheckbox);
        MaterialSwitch autoRotateCheckbox = view.findViewById(R.id.autoRotateCheckbox);
        MaterialSwitch autoScaleCheckbox = view.findViewById(R.id.autoScaleCheckbox);
        MaterialSwitch disableUsbAudioCheckbox = view.findViewById(R.id.disableUsbAudioCheckbox);

        boolean hasShizuku = ShizukuUtils.hasPermission();
        trustedDisplayCheckbox.setChecked(hasShizuku && Pref.getTrustedDisplay());
        trustedDisplayCheckbox.setEnabled(hasShizuku);
        trustedDisplayCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_TRUSTED_DISPLAY, c).apply());

        autoRotateCheckbox.setChecked(Pref.getAutoRotate());
        autoScaleCheckbox.setChecked(Pref.getAutoScale());
        disableUsbAudioCheckbox.setChecked(Pref.getDisableUsbAudio());

        autoRotateCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_ROTATE, c).apply());
        autoScaleCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_SCALE, c).apply());

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
        _updateLiveControls(view);

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

    private void _updateLiveControls(View view) {
        VirtualDisplay vd = _getActiveVirtualDisplay();
        boolean active = vd != null;

        // Touchscreen button
        MaterialButton btn = view.findViewById(R.id.touchscreenBtn);
        btn.setEnabled(active);
        btn.setOnClickListener(v -> {
            VirtualDisplay d = _getActiveVirtualDisplay();
            if (d == null) return;
            Intent intent = new Intent(requireContext(), TouchscreenActivity.class);
            intent.putExtra("surface", d.getSurface());
            intent.putExtra("display", d.getDisplay().getDisplayId());
            startActivity(intent);
        });

    }

    private VirtualDisplay _getActiveVirtualDisplay() {
        if (State.mirrorVirtualDisplay != null) return State.mirrorVirtualDisplay;
        if (State.displaylinkState.getVirtualDisplay() != null) return State.displaylinkState.getVirtualDisplay();
        return null;
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
