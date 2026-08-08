package io.github.jqssun.displaymirror.dialog;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.github.jqssun.displaymirror.R;
import io.github.jqssun.displaymirror.TvFocus;

public class ResolutionSettingsDialog {
  public interface OnSave {
    void apply(int width, int height, int refreshRate);
  }

  public static void show(
      Context context,
      int titleRes,
      int explanationRes,
      int width,
      int height,
      int refreshRate,
      OnSave onSave) {
    View dialogView =
        LayoutInflater.from(context).inflate(R.layout.dialog_resolution_settings, null);
    TextView explanationText = dialogView.findViewById(R.id.resolution_explanation_text);
    EditText widthEditText = dialogView.findViewById(R.id.width_edit_text);
    EditText heightEditText = dialogView.findViewById(R.id.height_edit_text);
    EditText refreshRateEditText = dialogView.findViewById(R.id.refresh_rate_edit_text);
    Spinner presetSpinner = dialogView.findViewById(R.id.resolution_preset_spinner);

    explanationText.setText(explanationRes);
    widthEditText.setText(String.valueOf(width));
    heightEditText.setText(String.valueOf(height));
    refreshRateEditText.setText(String.valueOf(refreshRate));

    DisplayMetrics dm = new DisplayMetrics();
    ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
        .getDefaultDisplay()
        .getRealMetrics(dm);
    int shortSide = Math.min(dm.widthPixels, dm.heightPixels);
    int longSide = Math.max(dm.widthPixels, dm.heightPixels);

    String[] presets = {
      context.getString(R.string.quick_presets),
      context.getString(R.string.preset_portrait),
      context.getString(R.string.preset_landscape),
      "1080p",
      "1440p",
      "2160p"
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
                widthEditText.setText(String.valueOf(shortSide));
                heightEditText.setText(String.valueOf(longSide));
                break;
              case 2:
                widthEditText.setText(String.valueOf(longSide));
                heightEditText.setText(String.valueOf(shortSide));
                break;
              case 3:
                widthEditText.setText("1920");
                heightEditText.setText("1080");
                refreshRateEditText.setText("60");
                break;
              case 4:
                widthEditText.setText("2560");
                heightEditText.setText("1440");
                refreshRateEditText.setText("60");
                break;
              case 5:
                widthEditText.setText("3840");
                heightEditText.setText("2160");
                refreshRateEditText.setText("60");
                break;
            }
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });

    TvFocus.attach(
        new MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
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
                    onSave.apply(w, h, r);
                  } catch (NumberFormatException e) {
                    /* ignore */
                  }
                })
            .setNegativeButton(R.string.cancel, null)
            .show());
  }
}
