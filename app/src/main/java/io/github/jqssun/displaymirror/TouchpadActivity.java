package io.github.jqssun.displaymirror;

import android.accessibilityservice.GestureDescription;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.KeyEventHidden;
import android.view.MotionEvent;
import android.view.MotionEventHidden;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


import io.github.jqssun.displaymirror.job.StartTouchPad;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import dev.rikka.tools.refine.Refine;

public class TouchpadActivity extends AppCompatActivity {
    
    public static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;
    public static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT = 1;
    public static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2;
    private TextView touchpadArea;
    private ImageView cursorView;
    private int displayId;
    private static final String TAG = "TouchpadActivity";
    private float cursorX = 0;
    private float cursorY = 0;
    private WindowManager.LayoutParams cursorParams;
    private float halfWidth;
    private float halfHeight;
    private IInputManager inputManager;
    private GestureState gestureState = new GestureState();
    private boolean isCursorLocked = false;
    private Spinner modeSpinner;
    private static final int MODE_NORMAL = 0;
    private static final int MODE_CURSOR_LOCKED = 1;

    private static class GestureState {
        List<MotionEvent> allMotionEvents = new ArrayList<>();
        int lastReplayed = 0;
        boolean isSingleFinger;
        float initialTouchX = 0;
        float initialTouchY = 0;
    }

    private static class StrokePoint {
        float x;
        float y;
        long time;

        StrokePoint(float x, float y, long time) {
            this.x = x;
            this.y = y;
            this.time = time;
        }
    }

    public static boolean startTouchpad(Context context,int displayId, boolean dryRun) {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.Q && !ShizukuUtils.hasPermission()) {
            return false;
        }
        if (displayId == Display.DEFAULT_DISPLAY) {
            return false;
        }
        if (!Settings.canDrawOverlays(context)) {
            if (!dryRun) {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName())
                );
                context.startActivity(intent);
            }
            return false;
        }
        
        if (ShizukuUtils.hasShizukuStarted()) {
            if (!dryRun) {
                State.startNewJob(new StartTouchPad(displayId, context));
            }
            return true;
        }
        
        if (!TouchpadAccessibilityService.isAccessibilityServiceEnabled(context)) {
            if (!dryRun) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                context.startActivity(intent);
            }
            return false;
        }
        
        if (!dryRun) {
            if(TouchpadAccessibilityService.getInstance() != null) {
                Intent touchpadIntent = new Intent(context, TouchpadActivity.class);
                touchpadIntent.putExtra("display_id", displayId);
                context.startActivity(touchpadIntent);
                return true;
            }
            Intent serviceIntent = new Intent(context, TouchpadAccessibilityService.class);
            context.startService(serviceIntent);

            new Handler().postDelayed(() -> {
                if (TouchpadAccessibilityService.getInstance() == null) {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    context.startActivity(intent);
                } else {
                    Intent touchpadIntent = new Intent(context, TouchpadActivity.class);
                    touchpadIntent.putExtra("display_id", displayId);
                    context.startActivity(touchpadIntent);
                }
            }, 1000);
            
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_touchpad);

        modeSpinner = findViewById(R.id.modeSpinner);
        touchpadArea = findViewById(R.id.touchpad_area);
        updateHelp();

        displayId = getIntent().getIntExtra("display_id", Display.DEFAULT_DISPLAY);
        
        if (ShizukuUtils.hasPermission()) {
            inputManager = ServiceUtils.getInputManager();
        }
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display targetDisplay = displayManager.getDisplay(displayId);
        if (targetDisplay == null) {
            finish();
            return;
        }

        halfWidth = targetDisplay.getWidth() / 2.0f;
        halfHeight = targetDisplay.getHeight() / 2.0f;

        showMouseCursor(targetDisplay);

        touchpadArea = findViewById(R.id.touchpad_area);
        
        if (inputManager == null) {
            setupTouchListenerForAccessibility();
        } else {
            setupTouchListenerForInputManager();
        }
        
        ImageButton goDarkButton = findViewById(R.id.goDarkButton);
        goDarkButton.setOnClickListener(v -> toggleDarkMode());
        
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            performBackGesture(inputManager, displayId);
        });

        ImageButton homeButton = findViewById(R.id.homeButton);
        homeButton.setOnClickListener(v -> {
            launchSingleApp(this, displayId);
        });

        setupModeSpinner();

        Button exitButton = findViewById(R.id.exitButton);
        exitButton.setOnClickListener(v -> finish());

        if (ShizukuUtils.hasPermission()) {
            setFocus(inputManager, displayId);
        }

        Button switchModeButton = findViewById(R.id.switchModeButton);
        switchModeButton.setOnClickListener(v -> switchMode());
    }

    private void setupTouchListenerForAccessibility() {
        touchpadArea.setOnTouchListener((v, event) -> {
            if (gestureState.allMotionEvents.isEmpty()) {
                gestureState.initialTouchX = event.getX();
                gestureState.initialTouchY = event.getY();
            }

            float relativeX = event.getX() - gestureState.initialTouchX;
            float relativeY = event.getY() - gestureState.initialTouchY;

            float absoluteX = cursorX + halfWidth + relativeX * 2;
            float absoluteY = cursorY + halfHeight + relativeY * 2;
            float offsetX = absoluteX - event.getX();
            float offsetY = absoluteY - event.getY();

            MotionEvent copiedEventWithOffset = obtainMotionEventWithOffset(event, offsetX, offsetY);
            gestureState.allMotionEvents.add(copiedEventWithOffset);

            if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                Log.d(TAG, "Touch event ended, isSingleFinger: " + gestureState.isSingleFinger);
                if (!gestureState.isSingleFinger) {
                    boolean alwaysSingleFinger = true;
                    for (MotionEvent e : gestureState.allMotionEvents) {
                        if (e.getPointerCount() > 1) {
                            alwaysSingleFinger = false;
                        }
                    }
                    if (!isCursorLocked && alwaysSingleFinger && (Math.abs(relativeX) > 10 || Math.abs(relativeY) > 10)) {
                        // ignore
                    } else {
                        replayGestureViaAccessibility(gestureState.allMotionEvents, displayId);
                    }
                }
                gestureState.lastReplayed = 0;
                gestureState.isSingleFinger = false;
                gestureState.allMotionEvents.clear();
                return true;
            }

            if (!isCursorLocked) {
                // move cursor with single finger if not locked
                if (gestureState.isSingleFinger || (event.getPointerCount() == 1 && (gestureState.allMotionEvents.size() == 5 || Math.abs(relativeX) > 10 || Math.abs(relativeY) > 10))) {
                    if (gestureState.allMotionEvents.size() == 5 && Math.abs(relativeX) < 1 && Math.abs(relativeY) < 1) {
                        Log.d(TAG, "No movement detected");
                        return true;
                    }
                    gestureState.isSingleFinger = true;
                    if (event.getPointerCount() == 1) {
                        updateCursorPosition(relativeX * 0.5f, relativeY * 0.5f);
                        gestureState.initialTouchX = event.getX();
                        gestureState.initialTouchY = event.getY();
                    }
                    return true;
                }
            }
            return true;
        });
    }

    private void setupTouchListenerForInputManager() {
        touchpadArea.setOnTouchListener((v, event) -> {
            if (gestureState.allMotionEvents.isEmpty()) {
                gestureState.initialTouchX = event.getX();
                gestureState.initialTouchY = event.getY();
            }

            float relativeX = event.getX() - gestureState.initialTouchX;
            float relativeY = event.getY() - gestureState.initialTouchY;

            float absoluteX = cursorX + halfWidth + relativeX * 2;
            float absoluteY = cursorY + halfHeight + relativeY * 2;
            float offsetX = absoluteX - event.getX();
            float offsetY = absoluteY - event.getY();

            MotionEvent copiedEventWithOffset = obtainMotionEventWithOffset(event, offsetX, offsetY);
            gestureState.allMotionEvents.add(copiedEventWithOffset);

            if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                Log.d(TAG, "Touch event ended, isSingleFinger: " + gestureState.isSingleFinger);
                if (!gestureState.isSingleFinger) {
                    if (!isCursorLocked && gestureState.lastReplayed == 0 && (Math.abs(relativeX) > 10 || Math.abs(relativeY) > 10)) {
                        // ignore
                    } else {
                        replayBufferedEvents();
                    }
                }
                gestureState.lastReplayed = 0;
                gestureState.isSingleFinger = false;
                gestureState.allMotionEvents.clear();
                return true;
            }

            if (!isCursorLocked && gestureState.lastReplayed == 0) {
                // move cursor with single finger if not locked
                if (gestureState.isSingleFinger || (event.getPointerCount() == 1 && (gestureState.allMotionEvents.size() == 5 || Math.abs(relativeX) > 10 || Math.abs(relativeY) > 10))) {
                    if (gestureState.allMotionEvents.size() == 5 && Math.abs(relativeX) < 1 && Math.abs(relativeY) < 1) {
                        Log.d(TAG, "No movement detected");
                        return true;
                    }
                    gestureState.isSingleFinger = true;
                    if (event.getPointerCount() == 1) {
                        updateCursorPosition(relativeX * 0.5f, relativeY * 0.5f);
                        gestureState.initialTouchX = event.getX();
                        gestureState.initialTouchY = event.getY();
                    }
                    return true;
                }

                if (event.getPointerCount() == 1) {
                    // buffer it
                    return true;
                }
            }

            replayBufferedEvents();
            return true;
        });
    }

    public static void launchSingleApp(Context context, int displayId) {
        String lastPackageName = Pref.getSelectedAppPackage();
        if (lastPackageName == null || lastPackageName.isEmpty()) {
            return;
        }
        ServiceUtils.launchPackage(context, lastPackageName, displayId);
    }

    private void updateHelp() {
        String singleFingerAction;
        int selectedMode = modeSpinner.getSelectedItemPosition();
        
        switch (selectedMode) {
            case MODE_CURSOR_LOCKED:
                singleFingerAction = getString(R.string.touchpad_help_cursor_locked);
                break;
            default:
                singleFingerAction = getString(R.string.touchpad_help_move_cursor);
                break;
        }
        
        touchpadArea.setText(getString(R.string.touchpad_help_text, singleFingerAction));
    }

    private void replayBufferedEvents() {
        if (inputManager == null || gestureState.allMotionEvents.isEmpty()) {
            return;
        }

        setFocus(inputManager, displayId);

        for (int i = gestureState.lastReplayed; i < gestureState.allMotionEvents.size(); i++) {
            MotionEvent event = gestureState.allMotionEvents.get(i);
            
            Log.d(TAG, String.format(
                "Replaying event #%d [displayId=%d]: coords=(%.2f, %.2f), action=%d", 
                i, displayId, event.getX(), event.getY(), event.getAction()));
            
            MotionEventHidden eventHidden = Refine.unsafeCast(event);
            eventHidden.setDisplayId(displayId);
            inputManager.injectInputEvent(event, INJECT_INPUT_EVENT_MODE_ASYNC);
        }
        
        gestureState.lastReplayed = gestureState.allMotionEvents.size();
    }

    private static void injectKeyEvent(IInputManager inputManager, int displayId, int action, int keyCode, int repeat, int metaState, int injectMode) {
        setFocus(inputManager, displayId);
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(now, now, action, keyCode, repeat, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                InputDevice.SOURCE_KEYBOARD);
        KeyEventHidden eventHidden = Refine.unsafeCast(event);
        eventHidden.setDisplayId(displayId);
        inputManager.injectInputEvent(event, injectMode);
    }

    private void showMouseCursor(Display targetDisplay) {
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
        
        cursorView = new ImageView(this);
        cursorView.setImageResource(R.drawable.mouse_cursor);
        
        Context displayContext = createDisplayContext(targetDisplay);
        WindowManager windowManager = (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
        
        try {
            windowManager.addView(cursorView, cursorParams);
        } catch (Exception e) {
            Toast.makeText(this, R.string.show_cursor_failed, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to show mouse cursor: " + e.getMessage());
        }
    }

    private void updateCursorPosition(float deltaX, float deltaY) {
        cursorX += deltaX * 1.5f;
        cursorY += deltaY * 1.5f;
        
        if (cursorX < -halfWidth || cursorX > halfWidth || 
            cursorY < -halfHeight || cursorY > halfHeight) {
            Log.w(TAG, "Cursor out of bounds - position: (" + cursorX + ", " + cursorY + ")");
        }
        
        cursorX = Math.max(-halfWidth, Math.min(cursorX, halfWidth));
        cursorY = Math.max(-halfHeight, Math.min(cursorY, halfHeight));
        
        if (cursorView != null && cursorParams != null) {
            cursorParams.x = (int) cursorX;
            cursorParams.y = (int) cursorY;
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            try {
                windowManager.updateViewLayout(cursorView, cursorParams);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update cursor position: " + e.getMessage());
            }
        }
    }

    public static void performBackGesture(IInputManager inputManager, int displayId) {
        if (inputManager != null) {
            injectKeyEvent(inputManager, displayId, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0, INJECT_INPUT_EVENT_MODE_ASYNC);
            injectKeyEvent(inputManager, displayId, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, 0, INJECT_INPUT_EVENT_MODE_ASYNC);
            return;
        }
        TouchpadAccessibilityService accessibilityService = TouchpadAccessibilityService.getInstance();
        if (accessibilityService != null) {
            accessibilityService.performBackGesture(displayId);
        }
    }

    private void toggleDarkMode() {
        Intent intent = new Intent(this, PureBlackActivity.class);
        ActivityOptions options = ActivityOptions.makeBasic();
        startActivity(intent, options.toBundle());
    }

    public static void setFocus(IInputManager inputManager, int displayId) {
        try {
            if (inputManager != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceUtils.getActivityTaskManager().focusTopTask(displayId);
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    List<ActivityTaskManager.RootTaskInfo> taskInfos = ServiceUtils.getActivityTaskManager().getAllRootTaskInfosOnDisplay(displayId);
                    for (ActivityTaskManager.RootTaskInfo taskInfo : taskInfos) {
                        ServiceUtils.getActivityTaskManager().setFocusedRootTask(taskInfo.taskId);
                        break;
                    }
                } else {
                    List<Object> stackInfos = ServiceUtils.getActivityTaskManager().getAllStackInfosOnDisplay(displayId);
                    if (!stackInfos.isEmpty()) {
                        Object stackInfo = stackInfos.get(0);
                        Field stackIdField = stackInfo.getClass().getDeclaredField("stackId");
                        stackIdField.setAccessible(true);
                        int stackId = stackIdField.getInt(stackInfo);
                        ServiceUtils.getActivityTaskManager().setFocusedStack(stackId);
                    }
                }
            } else {
                TouchpadAccessibilityService accessibilityService = TouchpadAccessibilityService.getInstance();
                if (accessibilityService != null) {
                    accessibilityService.setFocus(displayId);
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "Failed to set focus", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cursorView != null && cursorView.getWindowToken() != null) {
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            windowManager.removeView(cursorView);
        }
    }

    private MotionEvent obtainMotionEventWithOffset(MotionEvent source, float offsetX, float offsetY) {
        int pointerCount = source.getPointerCount();
        
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerCount];
        
        for (int i = 0; i < pointerCount; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            source.getPointerProperties(i, properties[i]);

            coords[i] = new MotionEvent.PointerCoords();
            source.getPointerCoords(i, coords[i]);
            coords[i].x += offsetX;
            coords[i].y += offsetY;
        }
        
        int DEFAULT_DEVICE_ID = 0;
        return MotionEvent.obtain(
            source.getDownTime(),
            source.getEventTime(),
            source.getAction(),
            pointerCount,
            properties,
            coords,
            source.getMetaState(),
            source.getButtonState(),
            source.getXPrecision(),
            source.getYPrecision(),
            DEFAULT_DEVICE_ID,
            source.getEdgeFlags(),
            source.getSource(),
            source.getFlags()
        );
    }

    private void setupModeSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            new String[]{getString(R.string.mode_normal), getString(R.string.mode_cursor_locked)}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(adapter);
        
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case MODE_NORMAL:
                        isCursorLocked = false;
                        if (cursorView != null) {
                            cursorView.setVisibility(View.VISIBLE);
                        }
                        break;
                    case MODE_CURSOR_LOCKED:
                        isCursorLocked = true;
                        if (cursorView != null) {
                            cursorView.setVisibility(View.GONE);
                        }
                        break;
                }
                updateHelp();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cursorView != null) {
            cursorView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cursorView != null) {
            cursorView.setVisibility(View.VISIBLE);
        }
    }

    private void switchMode() {
        int currentMode = modeSpinner.getSelectedItemPosition();
        int nextMode = (currentMode + 1) % modeSpinner.getCount();
        modeSpinner.setSelection(nextMode);
    }

    public static void replayGestureViaAccessibility(List<MotionEvent> allMotionEvents, int displayId) {
        TouchpadAccessibilityService service = TouchpadAccessibilityService.getInstance();
        if (service == null || allMotionEvents.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }

        SparseArray<StrokePoint> startPoints = new SparseArray<>();
        SparseArray<StrokePoint> endPoints = new SparseArray<>();
        long baseTime = allMotionEvents.get(0).getDownTime();

        for (MotionEvent event : allMotionEvents) {
            int action = event.getActionMasked();
            int pointerIndex = event.getActionIndex();
            
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    int pointerId = event.getPointerId(pointerIndex);
                    startPoints.put(pointerId, new StrokePoint(
                        Math.max(0, event.getX(pointerIndex)),
                        Math.max(0, event.getY(pointerIndex)),
                        event.getEventTime() - baseTime
                    ));
                    break;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    pointerId = event.getPointerId(pointerIndex);
                    endPoints.put(pointerId, new StrokePoint(
                        Math.max(0, event.getX(pointerIndex)),
                        Math.max(0, event.getY(pointerIndex)),
                        event.getEventTime() - baseTime
                    ));
                    break;
                    
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        pointerId = event.getPointerId(i);
                        endPoints.put(pointerId, new StrokePoint(
                            Math.max(0, event.getX(i)),
                            Math.max(0, event.getY(i)),
                            event.getEventTime() - baseTime
                        ));
                    }
                    break;
            }
        }

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.setDisplayId(displayId);

        for (int i = 0; i < startPoints.size(); i++) {
            int pointerId = startPoints.keyAt(i);
            StrokePoint start = startPoints.get(pointerId);
            StrokePoint end = endPoints.get(pointerId);

            if (end == null) {
                // use last known position if no end point
                end = start;
            }

            Path strokePath = new Path();
            strokePath.moveTo(start.x, start.y);
            strokePath.lineTo(end.x, end.y);

            long duration = end.time - start.time;
            if (duration <= 0) duration = 100; // ensure 100+ ms

            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(
                strokePath,
                start.time,
                duration,
                false
            ));
        }

        if (startPoints.size() > 0) {
            GestureDescription gestureDescription = gestureBuilder.build();
            service.setFocus(displayId);
            service.dispatchGesture(gestureDescription, null, null);
        }
    }
}