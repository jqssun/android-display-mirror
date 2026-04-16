package io.github.jqssun.displaymirror;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;

public class MoonlightCursorOverlay {
    private static final String TAG = "MoonlightCursorOverlay";
    private static ImageView cursorView;
    private static WindowManager.LayoutParams cursorParams;
    private static WindowManager wm;
    private static int halfScreenWidth;
    private static int halfScreenHeight;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static void show() {
        handler.post(() -> {
            if (cursorView != null) return;
            Context context = State.getContext();
            if (context == null) return;

            wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Point realSize = new Point();
            wm.getDefaultDisplay().getRealSize(realSize);
            halfScreenWidth = realSize.x / 2;
            halfScreenHeight = realSize.y / 2;

            cursorParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            );
            cursorParams.gravity = Gravity.CENTER;
            cursorParams.x = 0;
            cursorParams.y = 0;

            cursorView = new ImageView(context);
            cursorView.setImageResource(R.drawable.mouse_cursor);

            try {
                wm.addView(cursorView, cursorParams);
            } catch (Exception e) {
                Log.e(TAG, "Failed to show cursor overlay", e);
                cursorView = null;
            }
        });
    }

    public static void update(float x, float y) {
        if (cursorView == null || cursorParams == null || wm == null) return;
        handler.post(() -> {
            if (cursorView == null) return;
            cursorParams.x = (int) x - halfScreenWidth;
            cursorParams.y = (int) y - halfScreenHeight;
            try {
                wm.updateViewLayout(cursorView, cursorParams);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update cursor", e);
            }
        });
    }

    public static void hide() {
        handler.post(() -> {
            if (cursorView != null && wm != null) {
                try {
                    wm.removeView(cursorView);
                } catch (Exception ignored) {
                }
                cursorView = null;
                cursorParams = null;
                wm = null;
            }
        });
    }
}
