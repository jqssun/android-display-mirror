package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.widget.ImageView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DisplayLinkFragment extends Fragment {
    private SharedPreferences preferences;
    private MaterialButton manageDisplayBtn;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.X, true));
        setReturnTransition(new com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.X, false));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_displaylink, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferences = requireContext().getSharedPreferences(Pref.PREF_NAME, Context.MODE_PRIVATE);
        manageDisplayBtn = view.findViewById(R.id.manageDisplayBtn);
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

        MaterialButton downloadApkBtn = view.findViewById(R.id.downloadApkBtn);
        MaterialButton importApkBtn = view.findViewById(R.id.importApkBtn);
        manageDisplayBtn.setOnClickListener(v ->
                ((MirrorMainActivity) requireActivity()).manageDisplayInExtend(
                        State.getDisplaylinkVirtualDisplayId(),
                        MirrorMainActivity.SCREEN_DISPLAYLINK));

        downloadApkBtn.setOnClickListener(v ->
            ((MirrorMainActivity) requireActivity()).downloadDisplayLink(downloadApkBtn));

        importApkBtn.setOnClickListener(v ->
            ((MirrorMainActivity) requireActivity()).importApk());

        // Resolution
        TextView currentResolutionText = view.findViewById(R.id.currentResolutionText);
        _updateResolutionText(currentResolutionText);
        MaterialButton resolutionButton = view.findViewById(R.id.resolutionButton);
        resolutionButton.setOnClickListener(v -> _showResolutionDialog(currentResolutionText));

        // APK URL
        EditText apkUrlEditText = view.findViewById(R.id.apkUrlEditText);
        apkUrlEditText.setText(Pref.getDisplaylinkApkUrl());
        apkUrlEditText.setOnFocusChangeListener((v, hasFocus) -> {
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
        TextView title = view.findViewById(R.id.libStatusTitle);
        TextView detail = view.findViewById(R.id.libStatusDetail);
        ImageView icon = view.findViewById(R.id.libStatusIcon);
        boolean imported = ApkImporter.areLibsImported(requireContext());
        title.setText(imported ? R.string.displaylink_libs_status_imported : R.string.displaylink_libs_status_missing);
        detail.setText(imported ? R.string.displaylink_libs_detail_imported : R.string.import_displaylink_libs_prompt);
        icon.setImageResource(imported ? R.drawable.ic_check_circle : R.drawable.ic_error);
        _updateManageDisplayButton();
    }

    private void _updateResolutionText(TextView textView) {
        textView.setText(getString(R.string.displaylink_output_format,
                Pref.getDisplaylinkWidth(), Pref.getDisplaylinkHeight(), Pref.getDisplaylinkRefreshRate()));
    }

    private void _showResolutionDialog(TextView currentResolutionText) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_resolution_settings, null);
        EditText widthEditText = dialogView.findViewById(R.id.widthEditText);
        EditText heightEditText = dialogView.findViewById(R.id.heightEditText);
        EditText refreshRateEditText = dialogView.findViewById(R.id.refreshRateEditText);
        Spinner presetSpinner = dialogView.findViewById(R.id.resolutionPresetSpinner);

        widthEditText.setText(String.valueOf(Pref.getDisplaylinkWidth()));
        heightEditText.setText(String.valueOf(Pref.getDisplaylinkHeight()));
        refreshRateEditText.setText(String.valueOf(Pref.getDisplaylinkRefreshRate()));

        String[] presets = {getString(R.string.quick_presets), "1080p", "1440p", "2160p", "Apple iPad"};
        ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, presets);
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(presetAdapter);
        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 1: widthEditText.setText("1920"); heightEditText.setText("1080"); refreshRateEditText.setText("60"); break;
                    case 2: widthEditText.setText("2560"); heightEditText.setText("1440"); refreshRateEditText.setText("60"); break;
                    case 3: widthEditText.setText("3840"); heightEditText.setText("2160"); refreshRateEditText.setText("60"); break;
                    case 4: widthEditText.setText("2048"); heightEditText.setText("1536"); refreshRateEditText.setText("60"); break;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.displaylink_resolution_title)
            .setView(dialogView)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                try {
                    int w = Integer.parseInt(widthEditText.getText().toString());
                    int h = Integer.parseInt(heightEditText.getText().toString());
                    int r = Math.max(24, Math.min(240, Integer.parseInt(refreshRateEditText.getText().toString())));
                    preferences.edit()
                        .putInt(Pref.KEY_DISPLAYLINK_WIDTH, w)
                        .putInt(Pref.KEY_DISPLAYLINK_HEIGHT, h)
                        .putInt(Pref.KEY_DISPLAYLINK_REFRESH_RATE, r)
                        .apply();
                    _updateResolutionText(currentResolutionText);
                } catch (NumberFormatException e) { /* ignore */ }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void _updateManageDisplayButton() {
        if (manageDisplayBtn == null) {
            return;
        }
        manageDisplayBtn.setVisibility(State.getDisplaylinkVirtualDisplayId() > 0 ? View.VISIBLE : View.GONE);
    }
}
