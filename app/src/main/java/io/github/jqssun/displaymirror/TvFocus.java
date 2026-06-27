package io.github.jqssun.displaymirror;

import android.app.Dialog;
import android.view.View;
import android.view.Window;
import android.widget.EditText;

public final class TvFocus {
    public static void attach(Dialog dialog) {
        if (dialog != null) attach(dialog.getWindow());
    }

    public static void attach(Window window) {
        if (window == null) return;
        View root = window.getDecorView();
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            _outline(oldFocus, false);
            _outline(newFocus, true);
        });
        _outline(root.findFocus(), true);
    }

    private static void _outline(View view, boolean focused) {
        if (view == null || view instanceof EditText) return;
        boolean show = focused && !view.isInTouchMode();
        view.setForeground(show ? view.getContext().getDrawable(R.drawable.focus_highlight) : null);
    }
}
