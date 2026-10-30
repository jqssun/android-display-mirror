package io.github.jqssun.displaymirror.displaylink;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import io.github.jqssun.displaymirror.MainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.R;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.dialog.ResolutionSettingsDialog;
import java.io.IOException;

public class DisplaylinkFragment extends Fragment {
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

    MaterialButton getPresenterBtn = view.findViewById(R.id.get_presenter_btn);
    MaterialButton importPresenterBtn = view.findViewById(R.id.import_presenter_btn);
    manageDisplayBtn.setOnClickListener(
        v ->
            ((MainActivity) requireActivity())
                .manageDisplayInExtend(
                    DisplaylinkState.getVirtualDisplayId(), MainActivity.SCREEN_DISPLAYLINK));

    getPresenterBtn.setOnClickListener(
        v ->
            ((MainActivity) requireActivity())
                .openStoreListing(
                    ApkImporter.PRESENTER_PACKAGE,
                    Uri.parse(
                        "https://play.google.com/store/apps/details?id="
                            + ApkImporter.PRESENTER_PACKAGE)));

    importPresenterBtn.setOnClickListener(v -> _importFromPresenter(importPresenterBtn));

    // resolution
    TextView currentResolutionText = view.findViewById(R.id.current_resolution_text);
    _updateResolutionText(currentResolutionText);
    MaterialButton resolutionButton = view.findViewById(R.id.resolution_button);
    resolutionButton.setOnClickListener(v -> _showResolutionDialog(currentResolutionText));
  }

  private void _importFromPresenter(MaterialButton btn) {
    btn.setEnabled(false);
    Context ctx = requireContext().getApplicationContext();
    new Thread(
            () -> {
              String err;
              try {
                err = ApkImporter.importFromPresenter(ctx);
              } catch (IOException e) {
                err = e.getMessage();
              }
              String result = err;
              btn.post(
                  () -> {
                    btn.setEnabled(true);
                    if (result == null) {
                      Toast.makeText(ctx, R.string.import_success, Toast.LENGTH_SHORT).show();
                      State.log("DisplayLink libraries imported from Presenter");
                    } else {
                      Toast.makeText(
                              ctx, ctx.getString(R.string.import_failed, result), Toast.LENGTH_LONG)
                          .show();
                      State.log("DisplayLink import error: " + result);
                    }
                  });
            })
        .start();
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
        requireContext(),
        R.string.displaylink_resolution_title,
        R.string.resolution_explanation,
        Pref.getDisplaylinkWidth(),
        Pref.getDisplaylinkHeight(),
        Pref.getDisplaylinkRefreshRate(),
        (w, h, r) -> {
          preferences
              .edit()
              .putInt(Pref.KEY_DISPLAYLINK_WIDTH, w)
              .putInt(Pref.KEY_DISPLAYLINK_HEIGHT, h)
              .putInt(Pref.KEY_DISPLAYLINK_REFRESH_RATE, r)
              .apply();
          _updateResolutionText(currentResolutionText);
        });
  }

  private void _updateManageDisplayButton() {
    if (manageDisplayBtn == null) {
      return;
    }
    manageDisplayBtn.setVisibility(
        DisplaylinkState.getVirtualDisplayId() > 0 ? View.VISIBLE : View.GONE);
  }
}
