package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEventHidden;
import android.view.MotionEvent;
import android.view.MotionEventHidden;
import android.view.Surface;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.graphics.Rect;
import android.util.Log;
import android.graphics.Matrix;
import android.view.View;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.KeyEvent;

import androidx.appcompat.app.AppCompatActivity;

import io.github.jqssun.displaymirror.shizuku.ServiceUtils;

import dev.rikka.tools.refine.Refine;

public class TouchscreenActivity extends AppCompatActivity {
    private Surface surface;
    private int displayId;
    private DirectDrawView drawView;
    private Bitmap bufferBitmap;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int updateCounter = 0;
    private long startTime = 0;
    private boolean finished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        android.widget.FrameLayout rootLayout = new android.widget.FrameLayout(this);
        rootLayout.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        drawView = new DirectDrawView(this);
        android.widget.FrameLayout.LayoutParams drawViewParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        drawView.setId(View.generateViewId());

        rootLayout.addView(drawView, drawViewParams);

        TextView exitText = new TextView(this);
        exitText.setId(View.generateViewId());
        exitText.setPadding(32, 32, 32, 32);
        exitText.setText(R.string.exit_vertical);
        exitText.setBackgroundColor(0x80888888);
        exitText.setTextColor(0xFFFFFFFF);

        TextView backText = new TextView(this);
        backText.setId(View.generateViewId());
        backText.setPadding(32, 32, 32, 32);
        backText.setText(R.string.back_vertical);
        backText.setBackgroundColor(0x80888888);
        backText.setTextColor(0xFFFFFFFF);

        android.widget.LinearLayout.LayoutParams backParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        backParams.setMargins(0, 0, 0, 16);

        android.widget.LinearLayout buttonLayout = new android.widget.LinearLayout(this);
        buttonLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.FrameLayout.LayoutParams buttonLayoutParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        buttonLayoutParams.gravity = Gravity.CENTER | android.view.Gravity.END;
        buttonLayoutParams.setMargins(0, 16, 16, 0);

        buttonLayout.addView(backText, backParams);
        buttonLayout.addView(exitText);

        rootLayout.addView(buttonLayout, buttonLayoutParams);

        setContentView(rootLayout);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ WindowInsetsController
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.navigationBars() |
                                android.view.WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // Android 10-: SYSTEM_UI_FLAG_FULLSCREEN already included
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        if (getIntent().hasExtra("display")) {
            displayId = getIntent().getIntExtra("display", -1);
            drawView.setDisplayId(displayId);
        }

        if (getIntent().hasExtra("surface")) {
            // surface of the virtual display
            surface = getIntent().getParcelableExtra("surface");
        }

        exitText.setOnClickListener(v -> {
            finished = true;
        });

        backText.setOnClickListener(v -> {
            long now = SystemClock.uptimeMillis();
            injectKeyEvent(KeyEvent.KEYCODE_BACK, now, now, KeyEvent.ACTION_DOWN);
            injectKeyEvent(KeyEvent.KEYCODE_BACK, now, now + 10, KeyEvent.ACTION_UP);
        });

        backText.setOnTouchListener(new View.OnTouchListener() {
            private static final int DOUBLE_CLICK_TIME = 300;
            private long lastClickTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    long clickTime = System.currentTimeMillis();
                    if (clickTime - lastClickTime < DOUBLE_CLICK_TIME) {
                        TouchpadActivity.launchSingleApp(TouchscreenActivity.this, displayId);
                        return true;
                    }
                    lastClickTime = clickTime;
                }
                return false;
            }
        });

        captureFromSurface();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> {
                        long now = SystemClock.uptimeMillis();
                        injectKeyEvent(KeyEvent.KEYCODE_BACK, now, now, KeyEvent.ACTION_DOWN);
                        injectKeyEvent(KeyEvent.KEYCODE_BACK, now, now + 10, KeyEvent.ACTION_UP);
                    }
            );
        }
    }

    public void updateImage(Bitmap bitmap) {
        if (bitmap != null) {
            drawView.setBitmap(bitmap);
            drawView.invalidate();

            if (updateCounter == 0) {
                startTime = System.currentTimeMillis();
            }

            updateCounter++;

            if (updateCounter >= 60) {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                Log.d("TouchscreenActivity", "60 frame updates took " + duration + "ms, avg " + (duration / 60.0) + "ms per frame");
                updateCounter = 0;
            }
        }
    }

    public void captureFromSurface() {
        if (surface == null || !surface.isValid()) {
            Log.e("TouchscreenActivity", "Surface is invalid or null");
            return;
        }

        if (finished) {
            finish();
            return;
        }

        if (bufferBitmap == null || bufferBitmap.isRecycled()) {
            DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            Display display = displayManager.getDisplay(displayId);
            bufferBitmap = Bitmap.createBitmap(
                    Math.max(display.getWidth(), display.getHeight()),
                    Math.min(display.getWidth(), display.getHeight()),
                    Bitmap.Config.ARGB_8888
            );
        }

        PixelCopy.request(
                surface,
                new Rect(0, 0, bufferBitmap.getWidth(), bufferBitmap.getHeight()),
                bufferBitmap,
                copyResult -> {
                    if (copyResult == PixelCopy.SUCCESS) {
                        Bitmap temp = drawView.bitmap;
                        updateImage(bufferBitmap);
                        bufferBitmap = temp;

                        handler.postDelayed(this::captureFromSurface, 30);
                    } else {
                        if (copyResult == 3) {
                            handler.postDelayed(this::captureFromSurface, 1000);
                        } else {
                            Log.e("TouchscreenActivity", "PixelCopy failed: " + copyResult);
                        }
                    }
                },
                handler
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (drawView.bitmap != null && !drawView.bitmap.isRecycled()) {
            drawView.bitmap.recycle();
            drawView.bitmap = null;
        }
        if (bufferBitmap != null && !bufferBitmap.isRecycled()) {
            bufferBitmap.recycle();
            bufferBitmap = null;
        }
    }

    private void injectKeyEvent(int keyCode, long downTime, long eventTime, int action) {
        IInputManager inputManager = ServiceUtils.getInputManager();
        TouchpadActivity.setFocus(inputManager, displayId);
        KeyEvent event = new KeyEvent(downTime, eventTime, action, keyCode, 0);
        KeyEventHidden eventHidden = Refine.unsafeCast(event);
        eventHidden.setDisplayId(displayId);
        inputManager.injectInputEvent(event, 0);
    }

    @Override
    public void onBackPressed() {
        long now = SystemClock.uptimeMillis();
        injectKeyEvent(KeyEvent.KEYCODE_BACK, now, now, KeyEvent.ACTION_DOWN);
        injectKeyEvent(KeyEvent.KEYCODE_BACK, now, now + 10, KeyEvent.ACTION_UP);
    }

    public static class DirectDrawView extends View {
        private final IInputManager inputManager;
        private Bitmap bitmap;
        private int displayId;
        private static final String TAG = "DirectDrawView";
        private Matrix transformMatrix = new Matrix();
        private Matrix inverseMatrix = new Matrix();

        public DirectDrawView(android.content.Context context) {
            super(context);
            setBackgroundColor(android.graphics.Color.BLACK);
            inputManager = ServiceUtils.getInputManager();
        }

        public void setDisplayId(int displayId) {
            this.displayId = displayId;
        }

        public void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        private void injectMotionEvent(MotionEvent motionEvent) {
            TouchpadActivity.setFocus(inputManager, displayId);
            MotionEventHidden motionEventHidden = Refine.unsafeCast(motionEvent);
            motionEventHidden.setDisplayId(displayId);
            inputManager.injectInputEvent(motionEvent, 0);
        }

        private int getDisplayRotation() {
            DisplayManager displayManager = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
            Display display = displayManager.getDisplay(displayId);
            return display.getRotation();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (bitmap == null || bitmap.isRecycled()) {
                return false;
            }

            MotionEvent transformedEvent = MotionEvent.obtain(event);

            int pointerCount = event.getPointerCount();

            float[] points = new float[pointerCount * 2];

            for (int i = 0; i < pointerCount; i++) {
                points[i * 2] = event.getX(i);
                points[i * 2 + 1] = event.getY(i);
            }

            Log.d("TouchscreenActivity", "Pre-mapping point: [" + points[0] + ", " + points[1] + "]");

            inverseMatrix.mapPoints(points);

            int rotation = getDisplayRotation();

            int bitmapWidth = bitmap.getWidth();
            int bitmapHeight = bitmap.getHeight();

            for (int i = 0; i < pointerCount; i++) {
                float x = points[i * 2];
                float y = points[i * 2 + 1];

                switch (rotation) {
                    case Surface.ROTATION_90:
                        points[i * 2] = y;
                        points[i * 2 + 1] = bitmapWidth - x;
                        break;
                    case Surface.ROTATION_180:
                        points[i * 2] = bitmapWidth - x;
                        points[i * 2 + 1] = bitmapHeight - y;
                        break;
                    case Surface.ROTATION_270:
                        points[i * 2] = bitmapHeight - y;
                        points[i * 2 + 1] = x;
                        break;
                    case Surface.ROTATION_0:
                    default:
                        break;
                }

                // determine max width and height based on rotation, ignore touch event if out of 50% range
                int maxWidth, maxHeight;
                if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                    maxWidth = bitmapHeight;
                    maxHeight = bitmapWidth;
                } else {
                    maxWidth = bitmapWidth;
                    maxHeight = bitmapHeight;
                }

                if (points[i * 2] < -maxWidth * 0.5 || points[i * 2] > maxWidth * 1.5 ||
                    points[i * 2 + 1] < -maxHeight * 0.5 || points[i * 2 + 1] > maxHeight * 1.5) {
                    Log.d("TouchscreenActivity", "Touch point too far out of bounds, ignoring event");
                    return true;
                }

                points[i * 2] = Math.max(0, Math.min(points[i * 2], maxWidth - 1));
                points[i * 2 + 1] = Math.max(0, Math.min(points[i * 2 + 1], maxHeight - 1));
            }

            Log.d("TouchscreenActivity", "Post-mapping point: [" + points[0] + ", " + points[1] + "], rotation: " + rotation);

            MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[pointerCount];
            MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[pointerCount];

            for (int i = 0; i < pointerCount; i++) {
                pointerCoords[i] = new MotionEvent.PointerCoords();
                pointerCoords[i].x = points[i * 2];
                pointerCoords[i].y = points[i * 2 + 1];
                pointerCoords[i].pressure = event.getPressure(i);
                pointerCoords[i].size = event.getSize(i);

                pointerProperties[i] = new MotionEvent.PointerProperties();
                pointerProperties[i].id = event.getPointerId(i);
                pointerProperties[i].toolType = event.getToolType(i);
            }

            MotionEvent newEvent = MotionEvent.obtain(
                    event.getDownTime(),
                    event.getEventTime(),
                    event.getAction(),
                    pointerCount,
                    pointerProperties,
                    pointerCoords,
                    event.getMetaState(),
                    event.getButtonState(),
                    event.getXPrecision(),
                    event.getYPrecision(),
                    event.getDeviceId(),
                    event.getEdgeFlags(),
                    event.getSource(),
                    event.getFlags()
            );

            injectMotionEvent(newEvent);

            newEvent.recycle();
            transformedEvent.recycle();

            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (bitmap != null && !bitmap.isRecycled()) {
                int viewWidth = getWidth();
                int viewHeight = getHeight();
                int bitmapWidth = bitmap.getWidth();
                int bitmapHeight = bitmap.getHeight();

                float scale;
                float dx = 0, dy = 0;

                if (bitmapWidth * viewHeight > viewWidth * bitmapHeight) {
                    // scale by width if bitmap is wider than view
                    scale = (float) viewWidth / bitmapWidth;
                    dy = (viewHeight - bitmapHeight * scale) * 0.5f;
                } else {
                    // scale by height if bitmap is taller than view
                    scale = (float) viewHeight / bitmapHeight;
                    dx = (viewWidth - bitmapWidth * scale) * 0.5f;
                }

                transformMatrix.reset();
                transformMatrix.setScale(scale, scale);
                transformMatrix.postTranslate(dx, dy);

                transformMatrix.invert(inverseMatrix);

                canvas.drawBitmap(bitmap, transformMatrix, null);
            }
        }
    }
}
