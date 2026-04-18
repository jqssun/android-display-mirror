package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

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

    private void _initSettings(View view) {
        MaterialSwitch trustedDisplayCheckbox = view.findViewById(R.id.trustedDisplayCheckbox);
        MaterialSwitch autoRotateCheckbox = view.findViewById(R.id.autoRotateCheckbox);
        MaterialSwitch autoScaleCheckbox = view.findViewById(R.id.autoScaleCheckbox);

        boolean hasShizuku = ShizukuUtils.hasPermission();
        trustedDisplayCheckbox.setChecked(hasShizuku && Pref.getTrustedDisplay());
        trustedDisplayCheckbox.setEnabled(hasShizuku);
        trustedDisplayCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_TRUSTED_DISPLAY, c).apply());

        autoRotateCheckbox.setChecked(Pref.getAutoRotate());
        autoScaleCheckbox.setChecked(Pref.getAutoScale());

        autoRotateCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_ROTATE, c).apply());
        autoScaleCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_SCALE, c).apply());

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
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jqssun/android-display-mirror"))));

        view.findViewById(R.id.shizukuBtn).setOnClickListener(v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rikkaapps/shizuku"))));

        view.findViewById(R.id.exitBtn).setOnClickListener(v -> {
            io.github.jqssun.displaymirror.job.AutoRotateAndScaleForDisplaylink.instance = null;
            io.github.jqssun.displaymirror.job.ExitAll.execute(requireActivity(), false);
        });
    }



}
