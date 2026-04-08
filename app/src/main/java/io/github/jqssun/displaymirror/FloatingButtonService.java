package io.github.jqssun.displaymirror;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import io.github.jqssun.displaymirror.shizuku.ServiceUtils;

public class FloatingButtonService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private int displayId;
    private float initialX, initialY;
    private float initialTouchX, initialTouchY;
    private static final String PREFS_NAME = "FloatingButtonPrefs";
    private static final String KEY_X = "button_x";
    private static final String KEY_Y = "button_y";
    private static final int FADE_DELAY = 5000;
    private Runnable fadeOutRunnable;
    private android.os.Handler handler;
    private boolean isReady = true;
    private static final float MOVE_THRESHOLD = 20;
    private static final long DOUBLE_TAP_TIMEOUT = 300;
    private long lastTapTime = 0;
    private boolean autoHide;

    public static void startForMirror() {
        if (!Pref.getShowFloatingInMirrorMode()) {
            return;
        }
        Intent intent = new Intent(State.getContext(), FloatingButtonService.class);
        intent.putExtra("display_id", Display.DEFAULT_DISPLAY);
        State.getContext().startService(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        State.floatingButtonService = this;
        if (intent != null) {
            displayId = intent.getIntExtra("display_id", -1);
            if (displayId != -1) {
                if (floatingView != null) {
                    this.onDestroy();
                }
                autoHide = Pref.getAutoHideFloatingBackButton();
                _createFloatingButton();
            }
        }
        return START_STICKY;
    }

    public void onDisplayRemoved(int displayId) {
        if (this.displayId == displayId) {
            stopSelf();
        }
    }

    public void onSingleAppLaunched() {
        resetButtonVisibility();
    }

    private void _createFloatingButton() {
        handler = new android.os.Handler();
        fadeOutRunnable = () -> _fadeOutButton();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_back_button, null);
        ImageView backButton = floatingView.findViewById(R.id.floating_back_image);
        backButton.setImageResource(R.drawable.ic_back);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        
        if (!Pref.getAutoRotate() && displayId != Display.DEFAULT_DISPLAY) {
            params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        params.x = prefs.getInt(KEY_X, 0);
        params.y = prefs.getInt(KEY_Y, 100);
        if (params.x < 0) {
            params.x = 0;
        }
        if (params.y < 0) {
            params.y = 0;
        }

        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(displayId);
        if (display == null) {
            stopSelf();
            return;
        }

        Context displayContext = createDisplayContext(display);
        windowManager = (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            State.log("Failed to add floating window: " + e.getMessage());
            stopSelf();
            return;
        }

        floatingView.setOnTouchListener((v, event) -> {
            resetButtonVisibility();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    params.x = (int) (initialX + (event.getRawX() - initialTouchX));
                    params.y = (int) (initialY + (event.getRawY() - initialTouchY));
                    windowManager.updateViewLayout(floatingView, params);
                    return true;

                case MotionEvent.ACTION_UP:
                    float totalMoveX = Math.abs(event.getRawX() - initialTouchX);
                    float totalMoveY = Math.abs(event.getRawY() - initialTouchY);
                    
                    if (!isReady) {
                        resetButtonVisibility();
                    } else if (totalMoveX < MOVE_THRESHOLD && totalMoveY < MOVE_THRESHOLD) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                            State.log("Returning to projected single app");
                            TouchpadActivity.launchSingleApp(FloatingButtonService.this, displayId);
                            lastTapTime = 0;
                        } else {
                            State.log("Triggered back navigation");
                            TouchpadActivity.performBackGesture(ServiceUtils.getInputManager(), displayId);
                            lastTapTime = currentTime;
                        }
                    }
                    return true;
            }
            return false;
        });

        if (autoHide) {
            _startFadeOutTimer();
        }
    }

    private void _startFadeOutTimer() {
        handler.removeCallbacks(fadeOutRunnable);
        handler.postDelayed(fadeOutRunnable, FADE_DELAY);
    }

    private void _fadeOutButton() {
        floatingView.animate()
                .alpha(0.0f)
                .setDuration(500)
                .withEndAction(() -> isReady = false)
                .start();
    }

    public void resetButtonVisibility() {
        if (autoHide) {
            floatingView.setAlpha(1.0f);
            isReady = true;
            _startFadeOutTimer();
        }
    }

    @Override
    public void onDestroy() {
        if (handler != null) {
            handler.removeCallbacks(fadeOutRunnable);
        }
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch(Exception e) {
                // ignore
            }
            floatingView = null;
        }
        State.floatingButtonService = null;
    }

    public void onDisplayChanged(int displayId) {
        if (this.displayId == displayId) {
            Log.i("FloatingButtonService", "detected display change, reset button visibility");
            resetButtonVisibility();
        }
    }
}