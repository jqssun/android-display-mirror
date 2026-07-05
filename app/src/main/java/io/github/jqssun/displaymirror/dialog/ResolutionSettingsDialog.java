package io.github.jqssun.displaymirror.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.R;
import io.github.jqssun.displaymirror.TvFocus;

public class ResolutionSettingsDialog {
  public static void show(Context context, Runnable onSaved) {
    View dialogView =
        LayoutInflater.from(context).inflate(R.layout.dialog_resolution_settings, null);
    EditText widthEditText = dialogView.findViewById(R.id.width_edit_text);
    EditText heightEditText = dialogView.findViewById(R.id.height_edit_text);
    EditText refreshRateEditText = dialogView.findViewById(R.id.refresh_rate_edit_text);
    Spinner presetSpinner = dialogView.findViewById(R.id.resolution_preset_spinner);

    widthEditText.setText(String.valueOf(Pref.getDisplaylinkWidth()));
    heightEditText.setText(String.valueOf(Pref.getDisplaylinkHeight()));
    refreshRateEditText.setText(String.valueOf(Pref.getDisplaylinkRefreshRate()));

    String[] presets = {
      context.getString(R.string.quick_presets), "1080p", "1440p", "2160p", "Apple iPad"
    };
    ArrayAdapter<String> presetAdapter =
        new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, presets);
    presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    presetSpinner.setAdapter(presetAdapter);
    presetSpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            switch (position) {
              case 1:
                widthEditText.setText("1920");
                heightEditText.setText("1080");
                refreshRateEditText.setText("60");
                break;
              case 2:
                widthEditText.setText("2560");
                heightEditText.setText("1440");
                refreshRateEditText.setText("60");
                break;
              case 3:
                widthEditText.setText("3840");
                heightEditText.setText("2160");
                refreshRateEditText.setText("60");
                break;
              case 4:
                widthEditText.setText("2048");
                heightEditText.setText("1536");
                refreshRateEditText.setText("60");
                break;
            }
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });

    TvFocus.attach(
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.displaylink_resolution_title)
            .setView(dialogView)
            .setPositiveButton(
                R.string.ok,
                (dialog, which) -> {
                  try {
                    int w = Integer.parseInt(widthEditText.getText().toString());
                    int h = Integer.parseInt(heightEditText.getText().toString());
                    int r =
                        Math.max(
                            24,
                            Math.min(
                                240, Integer.parseInt(refreshRateEditText.getText().toString())));
                    context
                        .getSharedPreferences(Pref.PREF_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(Pref.KEY_DISPLAYLINK_WIDTH, w)
                        .putInt(Pref.KEY_DISPLAYLINK_HEIGHT, h)
                        .putInt(Pref.KEY_DISPLAYLINK_REFRESH_RATE, r)
                        .apply();
                    onSaved.run();
                  } catch (NumberFormatException e) {
                    /* ignore */
                  }
                })
            .setNegativeButton(R.string.cancel, null)
            .show());
  }
}
