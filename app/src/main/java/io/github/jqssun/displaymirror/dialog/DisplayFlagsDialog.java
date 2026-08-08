package io.github.jqssun.displaymirror.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.R;
import io.github.jqssun.displaymirror.TvFocus;
import io.github.jqssun.displaymirror.job.DisplayFlags;
import java.util.LinkedHashMap;
import java.util.Map;

public class DisplayFlagsDialog {

  public static void show(Context context) {
    View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_display_flags, null);
    MaterialSwitch customizeSwitch = dialogView.findViewById(R.id.customize_flags_switch);
    LinearLayout list = dialogView.findViewById(R.id.flags_list);
    LayoutInflater inflater = LayoutInflater.from(context);

    Map<Integer, MaterialCheckBox> boxes = new LinkedHashMap<>();
    Map<Integer, View> rows = new LinkedHashMap<>();
    // guards listeners while checkboxes are set programmatically
    boolean[] updating = {false};
    int[] customMask = {Pref.getCustomDisplayFlags(DisplayFlags.auto())};

    for (DisplayFlags.Item item : DisplayFlags.CUSTOMIZABLE) {
      View row = inflater.inflate(R.layout.item_display_flag, list, false);
      ((TextView) row.findViewById(R.id.flag_name)).setText(item.nameRes);
      ((TextView) row.findViewById(R.id.flag_desc)).setText(item.descRes);
      MaterialCheckBox box = row.findViewById(R.id.flag_checkbox);
      boxes.put(item.bit, box);
      rows.put(item.bit, row);
      int bit = item.bit;
      box.setOnCheckedChangeListener(
          (b, checked) -> {
            if (updating[0]) {
              return;
            }
            if (checked) {
              _uncheckConflicts(boxes, bit);
            }
            customMask[0] = _mask(boxes);
          });
      row.setOnClickListener(v -> box.toggle());
      list.addView(row);
    }

    boolean custom = Pref.getCustomDisplayFlagsEnabled();
    customizeSwitch.setChecked(custom);
    _applyState(boxes, rows, custom ? customMask[0] : DisplayFlags.auto(), custom, updating);

    // automatic shows the computed set read-only, custom restores the editable set
    customizeSwitch.setOnCheckedChangeListener(
        (b, checked) ->
            _applyState(
                boxes, rows, checked ? customMask[0] : DisplayFlags.auto(), checked, updating));

    TvFocus.attach(
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.display_flags)
            .setView(dialogView)
            .setPositiveButton(
                R.string.ok,
                (dialog, which) -> {
                  Pref.setCustomDisplayFlagsEnabled(customizeSwitch.isChecked());
                  if (customizeSwitch.isChecked()) {
                    Pref.setCustomDisplayFlags(customMask[0]);
                  }
                })
            .setNegativeButton(R.string.cancel, null)
            .show());
  }

  private static void _applyState(
      Map<Integer, MaterialCheckBox> boxes,
      Map<Integer, View> rows,
      int mask,
      boolean enabled,
      boolean[] updating) {
    updating[0] = true;
    for (Map.Entry<Integer, MaterialCheckBox> entry : boxes.entrySet()) {
      entry.getValue().setChecked((mask & entry.getKey()) != 0);
      entry.getValue().setEnabled(enabled);
      rows.get(entry.getKey()).setClickable(enabled);
    }
    updating[0] = false;
  }

  private static void _uncheckConflicts(Map<Integer, MaterialCheckBox> boxes, int bit) {
    for (int[] pair : DisplayFlags.CONFLICTS) {
      if (pair[0] == bit) {
        boxes.get(pair[1]).setChecked(false);
      } else if (pair[1] == bit) {
        boxes.get(pair[0]).setChecked(false);
      }
    }
  }

  private static int _mask(Map<Integer, MaterialCheckBox> boxes) {
    int mask = 0;
    for (Map.Entry<Integer, MaterialCheckBox> entry : boxes.entrySet()) {
      if (entry.getValue().isChecked()) {
        mask |= entry.getKey();
      }
    }
    return mask;
  }
}
