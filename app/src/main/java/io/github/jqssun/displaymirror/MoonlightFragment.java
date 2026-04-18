package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.jqssun.displaymirror.job.AutoRotateAndScaleForDisplaylink;
import io.github.jqssun.displaymirror.job.ConnectToClient;
import io.github.jqssun.displaymirror.job.ExitAll;
import io.github.jqssun.displaymirror.job.SunshineMouse;
import io.github.jqssun.displaymirror.job.SunshineServer;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.List;

public class MoonlightFragment extends Fragment {
    private MaterialButton startBtn, stopBtn, manageDisplayBtn;
    private ImageView statusIcon;
    private TextView statusTitle, statusDetail;
    private SharedPreferences preferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.X, true));
        setReturnTransition(new com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.X, false));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_moonlight, container, false);
        preferences = requireContext().getSharedPreferences(Pref.PREF_NAME, Context.MODE_PRIVATE);

        statusIcon = view.findViewById(R.id.moonlightStatusIcon);
        statusTitle = view.findViewById(R.id.moonlightStatusTitle);
        statusDetail = view.findViewById(R.id.moonlightStatusDetail);
        startBtn = view.findViewById(R.id.startBtn);
        stopBtn = view.findViewById(R.id.stopBtn);
        manageDisplayBtn = view.findViewById(R.id.manageDisplayBtn);

        startBtn.setOnClickListener(v -> ((MirrorMainActivity) requireActivity()).startMirroring());
        stopBtn.setOnClickListener(v -> {
            if (AutoRotateAndScaleForDisplaylink.instance != null) {
                AutoRotateAndScaleForDisplaylink.instance.release();
            }
            ExitAll.execute(requireActivity(), true);
        });
        manageDisplayBtn.setOnClickListener(v ->
                ((MirrorMainActivity) requireActivity()).manageDisplayInExtend(
                        State.getMoonlightManagedDisplayId(),
                        MirrorMainActivity.SCREEN_MOONLIGHT));

        // Moonlight settings
        _initMoonlightSettings(view);

        State.uiState.observe(getViewLifecycleOwner(), this::_updateUI);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ((MirrorMainActivity) requireActivity()).refresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        SunshineServer.suppressPin = null;
    }

    private void _initMoonlightSettings(View view) {
        MaterialSwitch showCursorCheckbox = view.findViewById(R.id.showMoonlightCursorCheckbox);
        MaterialSwitch autoConnectCheckbox = view.findViewById(R.id.autoConnectClientCheckbox);
        LinearLayout clientConnectionContainer = view.findViewById(R.id.clientConnectionContainer);
        Spinner clientSpinner = view.findViewById(R.id.clientSpinner);
        MaterialButton connectClientButton = view.findViewById(R.id.connectClientButton);
        MaterialSwitch disableRemoteSubmixCheckbox = view.findViewById(R.id.disableRemoteSubmixCheckbox);

        showCursorCheckbox.setChecked(Pref.getShowMoonlightCursor());
        showCursorCheckbox.setOnCheckedChangeListener((b, c) -> {
            preferences.edit().putBoolean(Pref.KEY_SHOW_MOONLIGHT_CURSOR, c).apply();
            SunshineMouse.setShowCursor(c);
        });

        boolean autoConnect = Pref.getAutoConnectClient();
        autoConnectCheckbox.setChecked(autoConnect);
        clientConnectionContainer.setVisibility(autoConnect ? View.VISIBLE : View.GONE);
        autoConnectCheckbox.setOnCheckedChangeListener((b, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_AUTO_CONNECT_CLIENT, isChecked).apply();
            clientConnectionContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) _loadClientList(clientSpinner);
        });
        if (autoConnect) _loadClientList(clientSpinner);

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

        disableRemoteSubmixCheckbox.setChecked(Pref.getDisableRemoteSubmix());
        disableRemoteSubmixCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_DISABLE_REMOTE_SUBMIX, c).apply());

        MaterialSwitch autoMatchCheckbox = view.findViewById(R.id.autoMatchAspectRatioCheckbox);
        MaterialSwitch preventAutoLockCheckbox = view.findViewById(R.id.preventAutoLockCheckbox);

        boolean hasShizuku = ShizukuUtils.hasPermission();

        autoMatchCheckbox.setChecked(Pref.getAutoMatchAspectRatio());
        autoMatchCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_AUTO_MATCH_ASPECT_RATIO, c).apply());
        if (!hasShizuku) autoMatchCheckbox.setEnabled(false);

        preventAutoLockCheckbox.setChecked(Pref.getPreventAutoLock());
        preventAutoLockCheckbox.setOnCheckedChangeListener((b, c) -> preferences.edit().putBoolean(Pref.KEY_PREVENT_AUTO_LOCK, c).apply());
        if (!hasShizuku) preventAutoLockCheckbox.setEnabled(false);
    }

    private void _updateUI(MirrorUiState state) {
        if (state.errorStatusText != null) {
            statusIcon.setImageResource(R.drawable.ic_error);
            statusTitle.setText(state.errorStatusText);
            statusDetail.setText("");
            startBtn.setVisibility(View.GONE);
            stopBtn.setVisibility(View.GONE);
            return;
        }

        // Update status card based on state
        if (SunshineService.instance == null) {
            statusIcon.setImageResource(R.drawable.ic_error);
            statusTitle.setText(R.string.moonlight_status_idle);
            statusDetail.setText(R.string.moonlight_status_idle_detail);
        } else {
            boolean isProjecting = State.mirrorVirtualDisplay != null ||
                    State.displaylinkState.getVirtualDisplay() != null ||
                    State.lastSingleAppDisplay != 0;
            if (isProjecting) {
                statusIcon.setImageResource(R.drawable.ic_check_circle);
                statusTitle.setText(R.string.moonlight_status_casting);
                statusDetail.setText(R.string.moonlight_status_casting_detail);
            } else {
                statusIcon.setImageResource(R.drawable.ic_sync);
                statusTitle.setText(R.string.moonlight_status_waiting);
                StringBuilder detail = new StringBuilder(getString(R.string.moonlight_status_waiting_detail));
                try {
                    for (String ip : SunshineService.getAllWifiIpAddresses(requireContext())) {
                        detail.append("\nIP: ").append(ip);
                    }
                } catch (Throwable e) { /* ignore */ }
                statusDetail.setText(detail);
            }
        }

        startBtn.setVisibility(state.startBtnVisibility ? View.VISIBLE : View.GONE);
        stopBtn.setVisibility(state.stopBtnVisibility ? View.VISIBLE : View.GONE);
        int managedDisplayId = State.getMoonlightManagedDisplayId();
        manageDisplayBtn.setVisibility(managedDisplayId > 0 ? View.VISIBLE : View.GONE);
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
                    int pin = (int)(Math.random() * 9000) + 1000;
                    SunshineServer.suppressPin = String.valueOf(pin);
                    ConnectToClient.connect(pin);
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }
}
