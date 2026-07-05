package io.github.jqssun.displaymirror;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.transition.MaterialSharedAxis;
import io.github.jqssun.displaymirror.job.AcquireShizuku;
import io.github.jqssun.displaymirror.job.AirPlayService;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

public class OverviewFragment extends Fragment {

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
    setReenterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_overview, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    _initShizuku(view);
    _initOverlay(view);

    view.findViewById(R.id.moonlight_card)
        .setOnClickListener(
            v -> Navigation.findNavController(v).navigate(R.id.action_overview_to_moonlight));
    view.findViewById(R.id.airplay_card)
        .setOnClickListener(
            v -> Navigation.findNavController(v).navigate(R.id.action_overview_to_airplay));
    view.findViewById(R.id.displaylink_card)
        .setOnClickListener(
            v -> Navigation.findNavController(v).navigate(R.id.action_overview_to_displaylink));

    view.findViewById(R.id.touchscreen_row)
        .setOnClickListener(
            v -> {
              MirrorTouchscreenBridge.TargetInfo target =
                  MirrorTouchscreenBridge.getDefaultTarget();
              startActivity(MirrorTouchscreenBridge.createInternalIntent(requireContext(), target));
            });

    State.uiState.observe(getViewLifecycleOwner(), state -> _updateStatus(view));
  }

  @Override
  public void onResume() {
    super.onResume();
    if (getView() != null) {
      _updateShizuku(getView());
      _updateOverlay(getView());
      _updateStatus(getView());
    }
  }

  private void _initShizuku(View view) {
    MaterialButton btn = view.findViewById(R.id.shizuku_permission_btn);
    btn.setOnClickListener(v -> State.startNewJob(new AcquireShizuku()));
    _updateShizuku(view);
  }

  private void _updateShizuku(View view) {
    TextView status = view.findViewById(R.id.shizuku_status);
    MaterialButton btn = view.findViewById(R.id.shizuku_permission_btn);
    boolean started = ShizukuUtils.hasShizukuStarted();
    boolean hasPermission = ShizukuUtils.hasPermission();
    if (!started) {
      status.setText(R.string.status_not_started);
      btn.setVisibility(View.GONE);
    } else if (!hasPermission) {
      status.setText(
          getString(R.string.status_started_not_authorized_server, ShizukuUtils.getServerUid()));
      btn.setVisibility(View.VISIBLE);
    } else {
      status.setText(getString(R.string.status_authorized_server, ShizukuUtils.getServerUid()));
      btn.setVisibility(View.GONE);
    }
  }

  private void _initOverlay(View view) {
    MaterialButton btn = view.findViewById(R.id.overlay_permission_btn);
    btn.setOnClickListener(
        v ->
            startActivity(
                new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + requireContext().getPackageName()))));
    _updateOverlay(view);
  }

  private void _updateOverlay(View view) {
    TextView status = view.findViewById(R.id.overlay_status);
    MaterialButton btn = view.findViewById(R.id.overlay_permission_btn);
    boolean has = Settings.canDrawOverlays(requireContext());
    status.setText(has ? R.string.status_authorized : R.string.status_not_authorized);
    btn.setVisibility(has ? View.GONE : View.VISIBLE);
    _updatePermissionsCard(view);
  }

  private void _updatePermissionsCard(View view) {
    TextView title = view.findViewById(R.id.permissions_status_title);
    TextView detail = view.findViewById(R.id.permissions_status_detail);
    ImageView icon = view.findViewById(R.id.permissions_status_icon);
    boolean granted =
        ShizukuUtils.hasShizukuStarted()
            && ShizukuUtils.hasPermission()
            && Settings.canDrawOverlays(requireContext());
    title.setText(granted ? R.string.permissions_granted : R.string.permissions_not_granted);
    detail.setText(
        granted ? R.string.permissions_granted_detail : R.string.permissions_not_granted_detail);
    icon.setImageResource(granted ? R.drawable.ic_check_circle : R.drawable.ic_error);
  }

  private void _updateStatus(View view) {
    int activeColor =
        MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimary);
    int inactiveColor =
        MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant);

    // moonlight
    TextView moonlightStatus = view.findViewById(R.id.moonlight_status);
    ImageView moonlightIcon = view.findViewById(R.id.moonlight_icon);
    boolean moonlightActive = SunshineService.instance != null;
    boolean moonlightProjecting =
        moonlightActive
            && (State.mirrorVirtualDisplay != null
                || State.displaylinkState.getVirtualDisplay() != null
                || State.lastSingleAppDisplay != 0);
    if (moonlightProjecting) {
      moonlightStatus.setText(R.string.moonlight_status_casting);
    } else if (moonlightActive) {
      moonlightStatus.setText(R.string.moonlight_status_waiting);
    } else {
      moonlightStatus.setText(R.string.moonlight_status_idle);
    }
    moonlightIcon.setImageTintList(
        ColorStateList.valueOf(moonlightProjecting ? activeColor : inactiveColor));

    // airplay
    TextView airplayStatus = view.findViewById(R.id.airplay_status);
    ImageView airplayIcon = view.findViewById(R.id.airplay_icon);
    AirPlayService airplay = AirPlayService.getInstance();
    boolean airplayConnected = airplay != null && airplay.isConnected();
    if (airplayConnected) {
      airplayStatus.setText(R.string.airplay_connected);
    } else {
      airplayStatus.setText(R.string.airplay_no_devices);
    }
    airplayIcon.setImageTintList(
        ColorStateList.valueOf(airplayConnected ? activeColor : inactiveColor));

    // displaylink
    TextView displaylinkStatus = view.findViewById(R.id.displaylink_status);
    ImageView displaylinkIcon = view.findViewById(R.id.displaylink_icon);
    boolean displaylinkImported = ApkImporter.areLibsImported(requireContext());
    boolean displaylinkActive = State.displaylinkState.getVirtualDisplay() != null;
    if (displaylinkActive) {
      displaylinkStatus.setText(R.string.displaylink_libs_status_active);
    } else if (displaylinkImported) {
      displaylinkStatus.setText(R.string.displaylink_libs_status_imported);
    } else {
      displaylinkStatus.setText(R.string.displaylink_libs_status_missing);
    }
    displaylinkIcon.setImageTintList(
        ColorStateList.valueOf(displaylinkActive ? activeColor : inactiveColor));

    // touchscreen
    View touchscreenRow = view.findViewById(R.id.touchscreen_row);
    TextView touchscreenStatus = view.findViewById(R.id.touchscreen_status);
    boolean touchscreenAvailable = MirrorTouchscreenBridge.getDefaultTarget() != null;
    touchscreenStatus.setText(
        touchscreenAvailable
            ? R.string.touchscreen_control_desc
            : R.string.touchscreen_control_desc_unavailable);
    touchscreenRow.setEnabled(touchscreenAvailable);
    touchscreenRow.setAlpha(touchscreenAvailable ? 1f : 0.38f);
  }
}
