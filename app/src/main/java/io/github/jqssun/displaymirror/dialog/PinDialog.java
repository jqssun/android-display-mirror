package io.github.jqssun.displaymirror.dialog;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import io.github.jqssun.displaymirror.R;
import io.github.jqssun.displaymirror.TvFocus;
import java.util.function.Consumer;

public class PinDialog {
  // airplay device connect: free-form pin
  public static void showConnect(Context context, String title, Consumer<String> onSubmit) {
    TextInputLayout inputLayout = _pinInput(context, R.string.airplay_pin_hint);
    TvFocus.attach(
        new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(_pad(context, inputLayout))
            .setPositiveButton(
                R.string.connect,
                (dialog, which) ->
                    onSubmit.accept(inputLayout.getEditText().getText().toString().trim()))
            .setNegativeButton(R.string.cancel, null)
            .show());
  }

  // sunshine pairing: 4-digit numeric pin
  public static void showPairing(Context context, String pinCandidate, Consumer<String> onSubmit) {
    TextInputLayout inputLayout = _pinInput(context, R.string.enter_pin_hint);
    EditText input = inputLayout.getEditText();
    input.setInputType(InputType.TYPE_CLASS_NUMBER);
    input.setText(pinCandidate);
    input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4)});
    TvFocus.attach(
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.enter_pin_title)
            .setView(_pad(context, inputLayout))
            .setPositiveButton(
                R.string.ok,
                (dialog, which) -> {
                  String pin = input.getText().toString();
                  if (pin.length() == 4) {
                    onSubmit.accept(pin);
                  } else {
                    inputLayout.setError(context.getString(R.string.enter_pin_error));
                  }
                })
            .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel())
            .show());
  }

  private static TextInputLayout _pinInput(Context context, int hintRes) {
    TextInputLayout inputLayout =
        new TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
    inputLayout.setHint(hintRes);
    inputLayout.addView(new TextInputEditText(inputLayout.getContext()));
    return inputLayout;
  }

  private static FrameLayout _pad(Context context, View view) {
    int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
    FrameLayout container = new FrameLayout(context);
    container.setPadding(pad, pad / 2, pad, 0);
    container.addView(view);
    return container;
  }
}
