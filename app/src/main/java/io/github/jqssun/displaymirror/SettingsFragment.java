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
import io.github.jqssun.displaymirror.dialog.DisplayFlagsDialog;
import io.github.jqssun.displaymirror.job.ExitAll;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

public class SettingsFragment extends Fragment {
  private SharedPreferences preferences;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_settings, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    preferences = requireContext().getSharedPreferences(Pref.PREF_NAME, Context.MODE_PRIVATE);
    _initSettings(view);
  }

  private void _initSettings(View view) {
    MaterialSwitch trustedDisplayCheckbox = view.findViewById(R.id.trusted_display_checkbox);
    MaterialSwitch mirrorOnlyCheckbox = view.findViewById(R.id.mirror_only_checkbox);
    MaterialSwitch rotateWithContentCheckbox = view.findViewById(R.id.rotate_with_content_checkbox);
    MaterialSwitch cropBlackBordersCheckbox = view.findViewById(R.id.crop_black_borders_checkbox);
    MaterialSwitch autoMatchCheckbox = view.findViewById(R.id.auto_match_aspect_ratio_checkbox);
    MaterialSwitch preventAutoLockCheckbox = view.findViewById(R.id.prevent_auto_lock_checkbox);
    MaterialSwitch useGlobalAudioCaptureCheckbox =
        view.findViewById(R.id.use_global_audio_capture_checkbox);
    MaterialButton manageSystemDisplaySettingsBtn =
        view.findViewById(R.id.manage_system_display_settings_btn);

    boolean hasShizuku = ShizukuUtils.hasPermission();
    trustedDisplayCheckbox.setChecked(hasShizuku && Pref.getTrustedDisplay());
    trustedDisplayCheckbox.setEnabled(hasShizuku);
    trustedDisplayCheckbox.setOnCheckedChangeListener(
        (b, c) -> preferences.edit().putBoolean(Pref.KEY_TRUSTED_DISPLAY, c).apply());

    mirrorOnlyCheckbox.setChecked(Pref.getMirrorOnly());
    mirrorOnlyCheckbox.setOnCheckedChangeListener(
        (b, c) -> preferences.edit().putBoolean(Pref.KEY_MIRROR_ONLY, c).apply());

    rotateWithContentCheckbox.setChecked(Pref.getRotateWithContent());
    cropBlackBordersCheckbox.setChecked(Pref.getCropBlackBorders());

    rotateWithContentCheckbox.setOnCheckedChangeListener(
        (b, c) -> preferences.edit().putBoolean(Pref.KEY_ROTATE_WITH_CONTENT, c).apply());
    cropBlackBordersCheckbox.setOnCheckedChangeListener(
        (b, c) -> preferences.edit().putBoolean(Pref.KEY_CROP_BLACK_BORDERS, c).apply());

    autoMatchCheckbox.setChecked(Pref.getAutoMatchAspectRatio());
    autoMatchCheckbox.setEnabled(hasShizuku);
    autoMatchCheckbox.setOnCheckedChangeListener(
        (b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_MATCH_ASPECT_RATIO, c).apply());

    preventAutoLockCheckbox.setChecked(Pref.getPreventAutoLock());
    preventAutoLockCheckbox.setEnabled(hasShizuku);
    preventAutoLockCheckbox.setOnCheckedChangeListener(
        (b, c) -> preferences.edit().putBoolean(Pref.KEY_PREVENT_AUTO_LOCK, c).apply());

    useGlobalAudioCaptureCheckbox.setChecked(Pref.getUseGlobalAudioCapture());
    useGlobalAudioCaptureCheckbox.setOnCheckedChangeListener(
        (b, c) -> preferences.edit().putBoolean(Pref.KEY_USE_GLOBAL_AUDIO_CAPTURE, c).apply());

    manageSystemDisplaySettingsBtn.setOnClickListener(
        v -> ((MainActivity) requireActivity()).openExtendSettings());

    MaterialButton displayFlagsBtn = view.findViewById(R.id.display_flags_btn);
    displayFlagsBtn.setEnabled(hasShizuku);
    displayFlagsBtn.setOnClickListener(v -> DisplayFlagsDialog.show(requireContext()));

    // about
    TextView versionText = view.findViewById(R.id.version_text);
    try {
      String ver =
          requireContext()
              .getPackageManager()
              .getPackageInfo(requireContext().getPackageName(), 0)
              .versionName;
      versionText.setText(
          getString(R.string.version_format, ver, android.os.Build.VERSION.RELEASE));
    } catch (Exception e) {
      versionText.setText(R.string.version_unknown);
    }
    view.findViewById(R.id.website_link)
        .setOnClickListener(
            v ->
                startActivity(
                    new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/jqssun/android-display-mirror"))));

    view.findViewById(R.id.shizuku_btn)
        .setOnClickListener(
            v ->
                startActivity(
                    new Intent(
                        Intent.ACTION_VIEW, Uri.parse("https://github.com/rikkaapps/shizuku"))));

    view.findViewById(R.id.exit_btn)
        .setOnClickListener(
            v -> ExitAll.execute(requireActivity(), false));
  }
}
