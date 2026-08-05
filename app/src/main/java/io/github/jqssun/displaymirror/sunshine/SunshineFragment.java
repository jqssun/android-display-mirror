package io.github.jqssun.displaymirror.sunshine;

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
import com.google.android.material.materialswitch.MaterialSwitch;
import io.github.jqssun.displaymirror.MainActivity;
import io.github.jqssun.displaymirror.MirrorUiState;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.R;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.job.ExitAll;
import java.util.ArrayList;
import java.util.List;

public class SunshineFragment extends Fragment {
  private MaterialButton startBtn, stopBtn, manageDisplayBtn;
  private ImageView statusIcon;
  private TextView statusTitle, statusDetail;
  private SharedPreferences preferences;

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
    View view = inflater.inflate(R.layout.fragment_sunshine, container, false);
    preferences = requireContext().getSharedPreferences(Pref.PREF_NAME, Context.MODE_PRIVATE);

    statusIcon = view.findViewById(R.id.sunshine_status_icon);
    statusTitle = view.findViewById(R.id.sunshine_status_title);
    statusDetail = view.findViewById(R.id.sunshine_status_detail);
    startBtn = view.findViewById(R.id.start_btn);
    stopBtn = view.findViewById(R.id.stop_btn);
    manageDisplayBtn = view.findViewById(R.id.manage_display_btn);

    startBtn.setOnClickListener(v -> ((MainActivity) requireActivity()).startSunshine());
    stopBtn.setOnClickListener(v -> ExitAll.execute(requireActivity(), true));
    manageDisplayBtn.setOnClickListener(
        v ->
            ((MainActivity) requireActivity())
                .manageDisplayInExtend(
                    SunshineState.getVirtualDisplayId(), MainActivity.SCREEN_SUNSHINE));

    // sunshine settings
    _initSunshineSettings(view);

    State.uiState.observe(getViewLifecycleOwner(), this::_updateUI);
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    ((MainActivity) requireActivity()).refresh();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    SunshineServer.suppressPin = null;
  }

  private void _initSunshineSettings(View view) {
    EditText deviceNameEditText = view.findViewById(R.id.device_name_edit_text);
    deviceNameEditText.setText(Pref.getSunshineDeviceName());
    deviceNameEditText.setOnFocusChangeListener(
        (v, hasFocus) -> {
          if (!hasFocus) {
            String name = deviceNameEditText.getText().toString().trim();
            if (name.isEmpty()) {
              name = Pref.getDefaultSunshineDeviceName();
              deviceNameEditText.setText(name);
            }
            String stored = name.equals(Pref.getDefaultSunshineDeviceName()) ? "" : name;
            preferences.edit().putString(Pref.KEY_SUNSHINE_DEVICE_NAME, stored).apply();
          }
        });

    MaterialSwitch inputToExternalDisplayCheckbox =
        view.findViewById(R.id.input_to_external_display_checkbox);
    inputToExternalDisplayCheckbox.setChecked(Pref.getSunshineInputToExternalDisplay());
    inputToExternalDisplayCheckbox.setOnCheckedChangeListener(
        (b, c) ->
            preferences.edit().putBoolean(Pref.KEY_SUNSHINE_INPUT_TO_EXTERNAL_DISPLAY, c).apply());

    MaterialSwitch showCursorCheckbox = view.findViewById(R.id.show_sunshine_cursor_checkbox);
    MaterialSwitch autoConnectCheckbox = view.findViewById(R.id.auto_connect_client_checkbox);
    LinearLayout clientConnectionContainer = view.findViewById(R.id.client_connection_container);
    Spinner clientSpinner = view.findViewById(R.id.client_spinner);
    MaterialButton connectClientButton = view.findViewById(R.id.connect_client_button);

    showCursorCheckbox.setChecked(Pref.getSunshineShowCursor());
    showCursorCheckbox.setOnCheckedChangeListener(
        (b, c) -> {
          preferences.edit().putBoolean(Pref.KEY_SUNSHINE_SHOW_CURSOR, c).apply();
          SunshineMouse.setShowCursor(c);
        });

    boolean autoConnect = Pref.getSunshineAutoConnectClient();
    autoConnectCheckbox.setChecked(autoConnect);
    clientConnectionContainer.setVisibility(autoConnect ? View.VISIBLE : View.GONE);
    autoConnectCheckbox.setOnCheckedChangeListener(
        (b, isChecked) -> {
          preferences.edit().putBoolean(Pref.KEY_SUNSHINE_AUTO_CONNECT_CLIENT, isChecked).apply();
          clientConnectionContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
          if (isChecked) _loadClientList(clientSpinner);
        });
    if (autoConnect) _loadClientList(clientSpinner);

    connectClientButton.setOnClickListener(
        v -> {
          String selectedClient = (String) clientSpinner.getSelectedItem();
          if (selectedClient != null && !selectedClient.isEmpty()) {
            if (selectedClient.equals(getString(R.string.manual_input))) {
              ManualClientInputDialog.show(requireContext());
            } else {
              preferences
                  .edit()
                  .putString(Pref.KEY_SUNSHINE_SELECTED_CLIENT, selectedClient)
                  .apply();
              int pin = (int) (Math.random() * 9000) + 1000;
              SunshineServer.suppressPin = String.valueOf(pin);
              ConnectToClient.connect(pin);
            }
          }
        });
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

    if (!SunshineHost.isRunning()) {
      statusIcon.setImageResource(R.drawable.ic_error);
      statusTitle.setText(R.string.sunshine_status_idle);
      statusDetail.setText(R.string.sunshine_status_idle_detail);
    } else {
      boolean isProjecting = SunshineState.virtualDisplay != null;
      if (isProjecting) {
        statusIcon.setImageResource(R.drawable.ic_check_circle);
        statusTitle.setText(R.string.sunshine_status_casting);
        statusDetail.setText(R.string.sunshine_status_casting_detail);
      } else {
        statusIcon.setImageResource(R.drawable.ic_sync);
        statusTitle.setText(R.string.sunshine_status_waiting);
        StringBuilder detail =
            new StringBuilder(getString(R.string.sunshine_status_waiting_detail));
        try {
          for (String ip : SunshineHost.getWifiIpAddresses(requireContext())) {
            detail.append("\nIP: ").append(ip);
          }
        } catch (Throwable e) {
          /* ignore */
        }
        statusDetail.setText(detail);
      }
    }

    startBtn.setVisibility(state.startBtnVisibility ? View.VISIBLE : View.GONE);
    stopBtn.setVisibility(state.stopBtnVisibility ? View.VISIBLE : View.GONE);
    manageDisplayBtn.setVisibility(SunshineState.getVirtualDisplayId() > 0 ? View.VISIBLE : View.GONE);
  }

  private void _loadClientList(Spinner spinner) {
    String selectedClient = Pref.getSunshineSelectedClient();
    List<String> clients = new ArrayList<>();
    clients.add(getString(R.string.manual_input));
    if (!selectedClient.isEmpty()) clients.add(selectedClient);
    clients.addAll(SunshineState.discoveredClients);

    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, clients);
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

}
