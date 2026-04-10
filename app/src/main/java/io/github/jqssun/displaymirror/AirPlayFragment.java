package io.github.jqssun.displaymirror;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import io.github.jqssun.displaymirror.job.AirPlayService;

import java.util.List;

public class AirPlayFragment extends Fragment {
    private TextView statusTitle, statusDetail;
    private ImageView statusIcon;
    private Spinner deviceSpinner;
    private MaterialButton scanBtn, connectBtn;
    private LinearLayout manualLayout;
    private TextInputEditText manualIp, manualPort;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_airplay, container, false);

        statusTitle = view.findViewById(R.id.airplayStatusTitle);
        statusDetail = view.findViewById(R.id.airplayStatusDetail);
        statusIcon = view.findViewById(R.id.airplayStatusIcon);
        scanBtn = view.findViewById(R.id.airplayScanBtn);
        deviceSpinner = view.findViewById(R.id.airplayDeviceSpinner);
        connectBtn = view.findViewById(R.id.airplayConnectBtn);

        manualLayout = view.findViewById(R.id.airplayManualLayout);
        manualIp = view.findViewById(R.id.airplayManualIp);
        manualPort = view.findViewById(R.id.airplayManualPort);
        MaterialButton manualBtn = view.findViewById(R.id.airplayManualBtn);
        MaterialButton manualConnectBtn = view.findViewById(R.id.airplayManualConnectBtn);

        manualBtn.setOnClickListener(v -> {
            boolean show = manualLayout.getVisibility() != View.VISIBLE;
            manualLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        });

        AirPlayService airplay = AirPlayService.getInstance();

        manualConnectBtn.setOnClickListener(v -> {
            String ip = manualIp.getText() != null ? manualIp.getText().toString().trim() : "";
            if (ip.isEmpty()) {
                Toast.makeText(requireContext(), "Enter an IP address", Toast.LENGTH_SHORT).show();
                return;
            }
            String portStr = manualPort.getText() != null ? manualPort.getText().toString().trim() : "";
            int port = portStr.isEmpty() ? 7000 : Integer.parseInt(portStr);
            _showPinDialog(new AirPlayService.AirPlayDevice("Manual (" + ip + ")", ip, port));
        });

        airplay.setListener(new AirPlayService.AirPlayListener() {
            @Override
            public void onDeviceFound(String name, String ip, int port) {
                _updateDeviceList();
            }

            @Override
            public void onConnected() {
                _updateStatus(R.drawable.ic_check_circle, R.string.airplay_connected, R.string.airplay_connected_detail);
                connectBtn.setText(R.string.stop);
                connectBtn.setVisibility(View.VISIBLE);
                manualLayout.setVisibility(View.GONE);
            }

            @Override
            public void onDisconnected(String error) {
                _updateStatus(R.drawable.ic_error, R.string.airplay_no_devices, R.string.airplay_no_devices_detail);
                connectBtn.setText(R.string.connect);
                if (airplay.getDevices().isEmpty()) {
                    connectBtn.setVisibility(View.GONE);
                    deviceSpinner.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        scanBtn.setOnClickListener(v -> {
            scanBtn.setEnabled(false);
            _updateStatus(R.drawable.ic_sync, R.string.airplay_connecting, R.string.airplay_connecting_detail);
            airplay.discover();
            v.postDelayed(() -> {
                scanBtn.setEnabled(true);
                if (airplay.getDevices().isEmpty()) {
                    _updateStatus(R.drawable.ic_error, R.string.airplay_no_devices, R.string.airplay_no_devices_detail);
                }
            }, 6000);
        });

        connectBtn.setOnClickListener(v -> {
            if (airplay.isConnected()) {
                airplay.disconnect();
                if (getContext() != null) {
                    getContext().stopService(new android.content.Intent(getContext(), AirPlayForegroundService.class));
                }
                return;
            }
            AirPlayService.AirPlayDevice dev = _getSelectedDevice();
            if (dev == null) return;
            _showPinDialog(dev);
        });

        // Restore state if devices already discovered
        if (!airplay.getDevices().isEmpty()) {
            _updateDeviceList();
        }
        if (airplay.isConnected()) {
            _updateStatus(R.drawable.ic_check_circle, R.string.airplay_connected, R.string.airplay_connected_detail);
            connectBtn.setText(R.string.stop);
            connectBtn.setVisibility(View.VISIBLE);
        }

        return view;
    }

    private void _updateDeviceList() {
        List<AirPlayService.AirPlayDevice> devices = AirPlayService.getInstance().getDevices();
        ArrayAdapter<AirPlayService.AirPlayDevice> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, devices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);
        deviceSpinner.setVisibility(View.VISIBLE);
        connectBtn.setVisibility(View.VISIBLE);
        statusIcon.setImageResource(R.drawable.ic_check_circle);
        statusTitle.setText(devices.size() + " device(s) found");
        statusDetail.setText(R.string.airplay_devices_found_detail);
    }

    private void _showPinDialog(AirPlayService.AirPlayDevice dev) {
        EditText pinInput = new EditText(requireContext());
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        pinInput.setHint("Leave empty for PIN-less");
        pinInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(requireContext());
        container.setPadding(pad, pad, pad, 0);
        container.addView(pinInput);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Connect to " + dev.name)
            .setMessage("Enter the 4-digit PIN if shown on the receiver, or leave empty.")
            .setView(container)
            .setPositiveButton(R.string.connect, (dialog, which) -> {
                String pin = pinInput.getText().toString().trim();
                _updateStatus(R.drawable.ic_sync, R.string.airplay_connecting, R.string.airplay_connecting_detail);
                AirPlayService.getInstance().connect(dev.ip, dev.port, pin, 0, 0, 30);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
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
}
