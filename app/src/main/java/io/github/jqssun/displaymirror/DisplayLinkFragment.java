package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import io.github.jqssun.displaymirror.dialog.ResolutionSettingsDialog;

public class DisplayLinkFragment extends Fragment {
  private SharedPreferences preferences;
  private MaterialButton manageDisplayBtn;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setEnterTransition(
        new com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X, true));
    setReturnTransition(
        new com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X, false));
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_displaylink, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    preferences = requireContext().getSharedPreferences(Pref.PREF_NAME, Context.MODE_PRIVATE);
    manageDisplayBtn = view.findViewById(R.id.manage_display_btn);
    _init(view);
    State.uiState.observe(getViewLifecycleOwner(), state -> _updateManageDisplayButton());
  }

  @Override
  public void onResume() {
    super.onResume();
    View view = getView();
    if (view != null) _updateLibStatus(view);
  }

  private void _init(View view) {
    _updateLibStatus(view);
    State.logVersion.observe(getViewLifecycleOwner(), v -> _updateLibStatus(view));
    _updateManageDisplayButton();

    MaterialButton downloadApkBtn = view.findViewById(R.id.download_apk_btn);
    MaterialButton importApkBtn = view.findViewById(R.id.import_apk_btn);
    manageDisplayBtn.setOnClickListener(
        v ->
            ((MainActivity) requireActivity())
                .manageDisplayInExtend(
                    State.getDisplaylinkVirtualDisplayId(), MainActivity.SCREEN_DISPLAYLINK));

    downloadApkBtn.setOnClickListener(
        v -> ((MainActivity) requireActivity()).downloadDisplayLink(downloadApkBtn));

    importApkBtn.setOnClickListener(v -> ((MainActivity) requireActivity()).importApk());

    // resolution
    TextView currentResolutionText = view.findViewById(R.id.current_resolution_text);
    _updateResolutionText(currentResolutionText);
    MaterialButton resolutionButton = view.findViewById(R.id.resolution_button);
    resolutionButton.setOnClickListener(v -> _showResolutionDialog(currentResolutionText));

    // APK URL
    EditText apkUrlEditText = view.findViewById(R.id.apk_url_edit_text);
    apkUrlEditText.setText(Pref.getDisplaylinkApkUrl());
    apkUrlEditText.setOnFocusChangeListener(
        (v, hasFocus) -> {
          if (!hasFocus) {
            String url = apkUrlEditText.getText().toString().trim();
            if (url.isEmpty()) {
              url = Pref.DEFAULT_DISPLAYLINK_APK_URL;
              apkUrlEditText.setText(url);
            }
            preferences.edit().putString(Pref.KEY_DISPLAYLINK_APK_URL, url).apply();
          }
        });
  }

  private void _updateLibStatus(View view) {
    TextView title = view.findViewById(R.id.lib_status_title);
    TextView detail = view.findViewById(R.id.lib_status_detail);
    ImageView icon = view.findViewById(R.id.lib_status_icon);
    boolean imported = ApkImporter.areLibsImported(requireContext());
    title.setText(
        imported
            ? R.string.displaylink_libs_status_imported
            : R.string.displaylink_libs_status_missing);
    detail.setText(
        imported
            ? R.string.displaylink_libs_detail_imported
            : R.string.import_displaylink_libs_prompt);
    icon.setImageResource(imported ? R.drawable.ic_check_circle : R.drawable.ic_error);
    _updateManageDisplayButton();
  }

  private void _updateResolutionText(TextView textView) {
    textView.setText(
        getString(
            R.string.displaylink_output_format,
            Pref.getDisplaylinkWidth(),
            Pref.getDisplaylinkHeight(),
            Pref.getDisplaylinkRefreshRate()));
  }

  private void _showResolutionDialog(TextView currentResolutionText) {
    ResolutionSettingsDialog.show(
        requireContext(), () -> _updateResolutionText(currentResolutionText));
  }

  private void _updateManageDisplayButton() {
    if (manageDisplayBtn == null) {
      return;
    }
    manageDisplayBtn.setVisibility(
        State.getDisplaylinkVirtualDisplayId() > 0 ? View.VISIBLE : View.GONE);
  }
}
