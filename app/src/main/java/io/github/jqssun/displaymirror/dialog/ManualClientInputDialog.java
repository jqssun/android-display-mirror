package io.github.jqssun.displaymirror.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.R;
import io.github.jqssun.displaymirror.TvFocus;
import io.github.jqssun.displaymirror.job.ConnectToClient;
import io.github.jqssun.displaymirror.job.SunshineServer;

public class ManualClientInputDialog {
  public static void show(Context context) {
    View dialogView =
        LayoutInflater.from(context).inflate(R.layout.dialog_manual_client_input, null);
    EditText ipEditText = dialogView.findViewById(R.id.ip_edit_text);
    EditText portEditText = dialogView.findViewById(R.id.port_edit_text);
    portEditText.setText("42515");

    TvFocus.attach(
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.manual_input_client_title)
            .setView(dialogView)
            .setPositiveButton(
                R.string.ok,
                (dialog, which) -> {
                  String ip = ipEditText.getText().toString().trim();
                  String port = portEditText.getText().toString().trim();
                  if (!ip.isEmpty()) {
                    String addr = port.isEmpty() ? ip : ip + ":" + port;
                    context
                        .getSharedPreferences(Pref.PREF_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(Pref.KEY_SELECTED_CLIENT, addr)
                        .apply();
                    int pin = (int) (Math.random() * 9000) + 1000;
                    SunshineServer.suppressPin = String.valueOf(pin);
                    ConnectToClient.connect(pin);
                  }
                })
            .setNegativeButton(R.string.cancel, null)
            .show());
  }
}
