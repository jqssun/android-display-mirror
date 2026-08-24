package io.github.jqssun.displaymirror.airplay;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import io.github.jqssun.displaymirror.MainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.R;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.dialog.PinDialog;
import io.github.jqssun.displaymirror.dialog.ResolutionSettingsDialog;
import java.util.List;

public class AirPlayFragment extends Fragment {
  private TextView statusTitle, statusDetail;
  private ImageView statusIcon;
  private Spinner deviceSpinner;
  private MaterialButton scanBtn, connectBtn, manageDisplayBtn;
  private LinearLayout manualLayout;
  private TextInputEditText manualIp, manualPort;
  private AirPlayService.AirPlayDevice _pendingDevice;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setEnterTransition(
        new com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X, true));
    setReturnTransition(
        new com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X, false));
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_airplay, container, false);

    statusTitle = view.findViewById(R.id.airplay_status_title);
    statusDetail = view.findViewById(R.id.airplay_status_detail);
    statusIcon = view.findViewById(R.id.airplay_status_icon);
    scanBtn = view.findViewById(R.id.airplay_scan_btn);
    deviceSpinner = view.findViewById(R.id.airplay_device_spinner);
    connectBtn = view.findViewById(R.id.airplay_connect_btn);
    manageDisplayBtn = view.findViewById(R.id.manage_display_btn);

    manualLayout = view.findViewById(R.id.airplay_manual_layout);
    manualIp = view.findViewById(R.id.airplay_manual_ip);
    manualPort = view.findViewById(R.id.airplay_manual_port);
    MaterialButton manualBtn = view.findViewById(R.id.airplay_manual_btn);
    MaterialButton manualConnectBtn = view.findViewById(R.id.airplay_manual_connect_btn);

    MaterialSwitch airplay1ModeSwitch = view.findViewById(R.id.airplay1_mode_switch);
    airplay1ModeSwitch.setChecked(Pref.getAirPlay1Mode());
    airplaylib.Airplaylib.setAirPlay1Mode(Pref.getAirPlay1Mode());
    airplay1ModeSwitch.setOnCheckedChangeListener(
        (b, c) -> {
          Pref.getPreferences().edit().putBoolean(Pref.KEY_AIRPLAY1_MODE, c).apply();
          airplaylib.Airplaylib.setAirPlay1Mode(c);
        });
    manageDisplayBtn.setOnClickListener(
        v ->
            ((MainActivity) requireActivity())
                .manageDisplayInExtend(
                    AirPlayState.getVirtualDisplayId(), MainActivity.SCREEN_AIRPLAY));

    TextView resolutionText = view.findViewById(R.id.airplay_resolution_text);
    _updateResolutionText(resolutionText);
    view.findViewById(R.id.airplay_resolution_button)
        .setOnClickListener(
            v ->
                ResolutionSettingsDialog.show(
                    requireContext(),
                    R.string.airplay_resolution_title,
                    R.string.airplay_resolution_explanation,
                    Pref.getAirPlayWidth(),
                    Pref.getAirPlayHeight(),
                    Pref.getAirPlayRefreshRate(),
                    (w, h, r) -> {
                      Pref.getPreferences()
                          .edit()
                          .putInt(Pref.KEY_AIRPLAY_WIDTH, w)
                          .putInt(Pref.KEY_AIRPLAY_HEIGHT, h)
                          .putInt(Pref.KEY_AIRPLAY_REFRESH_RATE, r)
                          .apply();
                      _updateResolutionText(resolutionText);
                    }));

    manualBtn.setOnClickListener(
        v -> {
          boolean show = manualLayout.getVisibility() != View.VISIBLE;
          manualLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        });

    AirPlayService airplay = AirPlayService.getInstance();

    manualConnectBtn.setOnClickListener(
        v -> {
          String ip = manualIp.getText() != null ? manualIp.getText().toString().trim() : "";
          if (ip.isEmpty()) {
            Toast.makeText(requireContext(), "Enter an IP address", Toast.LENGTH_SHORT).show();
            return;
          }
          String portStr =
              manualPort.getText() != null ? manualPort.getText().toString().trim() : "";
          int port = portStr.isEmpty() ? 7000 : Integer.parseInt(portStr);
          _pendingDevice = new AirPlayService.AirPlayDevice("Manual (" + ip + ")", ip, port);
          _updateStatus(
              R.drawable.ic_sync, R.string.airplay_connecting, R.string.airplay_connecting_detail);
          AirPlayService.getInstance().connect(_pendingDevice.ip, _pendingDevice.port, "");
        });

    airplay.setListener(
        new AirPlayService.AirPlayListener() {
          @Override
          public void onDeviceFound(String name, String ip, int port) {
            _updateDeviceList();
          }

          @Override
          public void onConnected() {
            _pendingDevice = null;
            _updateStatus(
                R.drawable.ic_check_circle,
                R.string.airplay_connected,
                R.string.airplay_connected_detail);
            connectBtn.setText(R.string.stop);
            connectBtn.setVisibility(View.VISIBLE);
            manualLayout.setVisibility(View.GONE);
            _updateManageDisplayButton();
          }

          @Override
          public void onDisconnected(String error) {
            _pendingDevice = null;
            _updateStatus(
                R.drawable.ic_error,
                R.string.airplay_no_devices,
                R.string.airplay_no_devices_detail);
            connectBtn.setText(R.string.connect);
            if (airplay.getDevices().isEmpty()) {
              connectBtn.setVisibility(View.GONE);
              deviceSpinner.setVisibility(View.GONE);
            }
            _updateManageDisplayButton();
          }

          @Override
          public void onError(String error) {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
          }

          @Override
          public void onPinRequired() {
            if (_pendingDevice == null) return;
            _showPinDialog(_pendingDevice);
          }
        });

    scanBtn.setOnClickListener(
        v -> {
          scanBtn.setEnabled(false);
          _updateStatus(
              R.drawable.ic_sync, R.string.airplay_connecting, R.string.airplay_connecting_detail);
          airplay.discover();
          v.postDelayed(
              () -> {
                scanBtn.setEnabled(true);
                if (airplay.getDevices().isEmpty()) {
                  _updateStatus(
                      R.drawable.ic_error,
                      R.string.airplay_no_devices,
                      R.string.airplay_no_devices_detail);
                }
              },
              6000);
        });

    connectBtn.setOnClickListener(
        v -> {
          if (airplay.isConnected()) {
            airplay.disconnect();
            return;
          }
          AirPlayService.AirPlayDevice dev = _getSelectedDevice();
          if (dev == null) return;
          _pendingDevice = dev;
          _updateStatus(
              R.drawable.ic_sync, R.string.airplay_connecting, R.string.airplay_connecting_detail);
          airplay.connect(dev.ip, dev.port, "");
        });

    // restore state if devices already discovered
    if (!airplay.getDevices().isEmpty()) {
      _updateDeviceList();
    }
    if (airplay.isConnected()) {
      _updateStatus(
          R.drawable.ic_check_circle,
          R.string.airplay_connected,
          R.string.airplay_connected_detail);
      connectBtn.setText(R.string.stop);
      connectBtn.setVisibility(View.VISIBLE);
    }
    State.uiState.observe(getViewLifecycleOwner(), state -> _updateManageDisplayButton());
    _updateManageDisplayButton();

    return view;
  }

  private void _updateDeviceList() {
    List<AirPlayService.AirPlayDevice> devices = AirPlayService.getInstance().getDevices();
    ArrayAdapter<AirPlayService.AirPlayDevice> adapter =
        new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, devices);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    deviceSpinner.setAdapter(adapter);
    deviceSpinner.setVisibility(View.VISIBLE);
    connectBtn.setVisibility(View.VISIBLE);
    statusIcon.setImageResource(R.drawable.ic_check_circle);
    statusTitle.setText(devices.size() + " device(s) found");
    statusDetail.setText(R.string.airplay_devices_found_detail);
  }

  private void _showPinDialog(AirPlayService.AirPlayDevice dev) {
    PinDialog.showConnect(
        requireContext(),
        getString(R.string.airplay_connect_title, dev.name),
        pin -> {
          _pendingDevice = dev;
          _updateStatus(
              R.drawable.ic_sync, R.string.airplay_connecting, R.string.airplay_connecting_detail);
          AirPlayService.getInstance().submitPin(pin);
        });
  }

  private void _updateResolutionText(TextView textView) {
    textView.setText(
        getString(
            R.string.airplay_output_format,
            Pref.getAirPlayWidth(),
            Pref.getAirPlayHeight(),
            Pref.getAirPlayRefreshRate()));
  }

  private void _updateStatus(int iconRes, int titleRes, int detailRes) {
    statusIcon.setImageResource(iconRes);
    statusTitle.setText(titleRes);
    statusDetail.setText(detailRes);
  }

  private AirPlayService.AirPlayDevice _getSelectedDevice() {
    Object item = deviceSpinner.getSelectedItem();
    if (item instanceof AirPlayService.AirPlayDevice) {
      return (AirPlayService.AirPlayDevice) item;
    }
    return null;
  }

  private void _updateManageDisplayButton() {
    if (manageDisplayBtn == null) {
      return;
    }
    manageDisplayBtn.setVisibility(
        AirPlayState.getVirtualDisplayId() > 0 ? View.VISIBLE : View.GONE);
  }
}
