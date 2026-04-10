package io.github.jqssun.displaymirror;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;

public class MoonlightCursorOverlay {
    private static final String TAG = "MoonlightCursorOverlay";
    private static ImageView cursorView;
    private static WindowManager.LayoutParams cursorParams;
    private static WindowManager wm;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static void show() {
        handler.post(() -> {
            if (cursorView != null) return;
            Context context = State.getContext();
            if (context == null) return;

            cursorParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            );
            cursorParams.x = 0;
            cursorParams.y = 0;

            cursorView = new ImageView(context);
            cursorView.setImageDrawable(new BitmapDrawable(context.getResources(), _createCursorBitmap()));

            wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
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
            // LayoutParams x/y are offsets from center of screen
            android.util.DisplayMetrics dm = cursorView.getResources().getDisplayMetrics();
            cursorParams.x = (int) x - dm.widthPixels / 2;
            cursorParams.y = (int) y - dm.heightPixels / 2;
            try {
                wm.updateViewLayout(cursorView, cursorParams);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update cursor", e);
            }
        });
    }

    private static Bitmap _createCursorBitmap() {
        int w = 24, h = 32;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Path arrow = new Path();
        arrow.moveTo(0, 0);
        arrow.lineTo(0, h);
        arrow.lineTo(w * 0.55f, h * 0.7f);
        arrow.lineTo(w, h);
        arrow.lineTo(w * 0.65f, h * 0.55f);
        arrow.lineTo(w, h * 0.35f);
        arrow.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.WHITE);
        fill.setStyle(Paint.Style.FILL);
        c.drawPath(arrow, fill);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.BLACK);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1.5f);
        c.drawPath(arrow, border);
        return bmp;
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
