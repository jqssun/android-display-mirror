package io.github.jqssun.displaymirror.job;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.IWindowManager;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEventHidden;
import android.view.Surface;

import androidx.annotation.NonNull;

import io.github.jqssun.displaymirror.MoonlightCursorOverlay;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.SunshineService;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dev.rikka.tools.refine.Refine;


public class SunshineMouse {
    private static String TAG = "SunshineMouse";
    public static AutoRotateAndScaleForMoonlight autoRotateAndScaleForMoonlight;
    private static IInputManager inputManager;
    private static float defaultDisplayWidth;
    private static float defaultDisplayHeight;
    // screenWidth * screenHeight always in landscape mode
    private static float screenWidth;
    private static float screenHeight;
    private static float portraitMirrorWidth;
    private static float portraitMirrorHeight;
    private static float landscapeMirrorWidth;
    private static float landscapeMirrorHeight;
    private static boolean autoScale;
    private static boolean autoRotate;
    private static boolean showCursor;

    public static void initialize(int width, int height) {
        Context context = State.getContext();
        if (context == null) {
            return;
        }
        if (ShizukuUtils.hasPermission()) {
            inputManager = ServiceUtils.getInputManager();
        }
        screenWidth = width;
        screenHeight = height;
        autoRotate = Pref.getAutoRotate();
        autoScale = Pref.getAutoScale();
        showCursor = Pref.getShowMoonlightCursor();
        if (showCursor) {
            MoonlightCursorOverlay.show();
        }

        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (State.mirrorVirtualDisplay == null && Pref.getAutoMatchAspectRatio() && ShizukuUtils.hasPermission()) {
            CreateVirtualDisplay.changeAspectRatio(width, height);
            IWindowManager windowManager = ServiceUtils.getWindowManager();
            android.graphics.Point baseSize = new android.graphics.Point();
            windowManager.getBaseDisplaySize(Display.DEFAULT_DISPLAY, baseSize);
            defaultDisplayWidth = Math.max(baseSize.x, baseSize.y);
            defaultDisplayHeight = Math.min(baseSize.x, baseSize.y);
            float aspectRatio1 = defaultDisplayWidth / defaultDisplayHeight;
            float aspectRatio2 = screenWidth / screenHeight;
            if (Math.abs(aspectRatio1 - aspectRatio2) > 0.01) {
                // change resolution to avoid stretching
                defaultDisplayWidth = screenWidth;
                DisplayCutout cutout = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    cutout = defaultDisplay.getCutout();
                }
                if (cutout != null) {
                    for(Rect rect : cutout.getBoundingRects()) {
                        if (rect.top == 0) {
                            defaultDisplayWidth += rect.bottom * 2;
                            break;
                        }
                    }
                }
            }
        } else {
            android.graphics.Point realSize = new android.graphics.Point();
            defaultDisplay.getRealSize(realSize);
            defaultDisplayWidth = Math.max(realSize.x, realSize.y);
            defaultDisplayHeight = Math.min(realSize.x, realSize.y);
        }
        float aspectRatio = defaultDisplayWidth / defaultDisplayHeight;

        landscapeMirrorHeight = screenHeight;
        landscapeMirrorWidth = landscapeMirrorHeight * aspectRatio;
        if (landscapeMirrorWidth > screenWidth) {
            landscapeMirrorWidth = screenWidth;
            landscapeMirrorHeight = landscapeMirrorWidth / aspectRatio;
        }

        portraitMirrorHeight = screenHeight;
        portraitMirrorWidth = portraitMirrorHeight / aspectRatio;
        if (portraitMirrorWidth > screenWidth) {
            portraitMirrorWidth = screenWidth;
            portraitMirrorHeight = portraitMirrorWidth * aspectRatio;
        }

        State.log("Primary display size defaultDisplayWidth: " + defaultDisplayWidth + " defaultDisplayHeight: " + defaultDisplayHeight);
        State.log("Client screen size screenWidth: " + screenWidth + " screenHeight: " + screenHeight);
        if (State.mirrorVirtualDisplay == null) {
            State.log("Mirror mode portraitMirrorWidth: " + portraitMirrorWidth + " portraitMirrorHeight: " + portraitMirrorHeight + " landscapeMirrorWidth: " + landscapeMirrorWidth + " landscapeMirrorHeight: " + landscapeMirrorHeight);
        }
    }


    private static class Point {
        public float x = 0;
        public float y = 0;
    }

    private static Map<Integer, Point> pointers = new HashMap<>();

    private static Point _translate(float x, float y) {
        if (State.mirrorVirtualDisplay != null) {
            return _translateVirtualDisplay(x, y);
        } else {
            return _translateMirrorMode(x, y);
        }
    }

    private static Point _translateMirrorMode(float x, float y) {
        boolean isLandscape = SunshineService.instance.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        float xInScreen = x * screenWidth;
        float yInScreen = y * screenHeight;
        if (isLandscape) {
            return _translateRotation90Mirror(xInScreen, yInScreen);
        } else {
            return _translateRotation0Mirror(xInScreen, yInScreen);
        }
    }

    private static Point _translateRotation0Mirror(float xInScreen, float yInScreen) {
        if (autoRotate) {
            Point point = new Point();
            float xBlackBar = (screenWidth - landscapeMirrorWidth) / 2;
            float yBlackBar = (screenHeight - landscapeMirrorHeight) / 2;
            float adjustedX = xInScreen - xBlackBar;
            if (adjustedX > landscapeMirrorWidth) {
                adjustedX = landscapeMirrorWidth;
            } else if (adjustedX < 0) {
                adjustedX = 0;
            }
            float adjustedY = yInScreen - yBlackBar;
            if (adjustedY > landscapeMirrorHeight) {
                adjustedY = landscapeMirrorHeight;
            } else if (adjustedY < 0) {
                adjustedY = 0;
            }
            point.y = (adjustedX / landscapeMirrorWidth) * defaultDisplayWidth;
            point.x = (1 - (adjustedY / landscapeMirrorHeight)) * defaultDisplayHeight;
            return point;
        } else {
            Point point = new Point();
            float xBlackBar = (screenWidth - portraitMirrorWidth) / 2;
            float yBlackBar = (screenHeight - portraitMirrorHeight) / 2;
            float adjustedX = xInScreen - xBlackBar;
            if (adjustedX > portraitMirrorWidth) {
                adjustedX = portraitMirrorWidth;
            } else if (adjustedX < 0) {
                adjustedX = 0;
            }
            float adjustedY = yInScreen - yBlackBar;
            if (adjustedY > portraitMirrorHeight) {
                adjustedY = portraitMirrorHeight;
            } else if (adjustedY < 0) {
                adjustedY = 0;
            }
            point.x = (adjustedX / portraitMirrorWidth) * defaultDisplayHeight;
            point.y = (adjustedY / portraitMirrorHeight) * defaultDisplayWidth;
            return point;
        }
    }

    private static Point _translateRotation90Mirror(float xInScreen, float yInScreen) {
        Point point = new Point();
        float xBlackBar = (screenWidth - landscapeMirrorWidth) / 2;
        float yBlackBar = (screenHeight - landscapeMirrorHeight) / 2;
        float adjustedX = xInScreen - xBlackBar;
        if (adjustedX > landscapeMirrorWidth) {
            adjustedX = landscapeMirrorWidth;
        } else if (adjustedX < 0) {
            adjustedX = 0;
        }
        float adjustedY = yInScreen - yBlackBar;
        if (adjustedY > landscapeMirrorHeight) {
            adjustedY = landscapeMirrorHeight;
        } else if (adjustedY < 0) {
            adjustedY = 0;
        }
        point.x = (adjustedX / landscapeMirrorWidth) * defaultDisplayWidth;
        point.y = (adjustedY / landscapeMirrorHeight) * defaultDisplayHeight;
        return point;
    }

    private static @NonNull Point _translateVirtualDisplay(float x, float y) {
        int displayRotation = State.mirrorVirtualDisplay.getDisplay().getRotation();
        Point point = new Point();
        switch (displayRotation) {
            case Surface.ROTATION_0:
                point.x = x * screenWidth;
                point.y = y * screenHeight;
                break;
            case Surface.ROTATION_90:
                point.x = y * screenHeight;
                point.y = (1 - x) * screenWidth;
                break;
            case Surface.ROTATION_180:
                point.x = (1 - x) * screenWidth;
                point.y = (1 - y) * screenHeight;
                break;
            case Surface.ROTATION_270:
                point.x = (1 - y) * screenHeight;
                point.y = x * screenWidth;
                break;
        }
        return point;
    }

    private static Point singlePoint = null;
    // cursor position in device coordinates for relative mouse
    private static Point cursorPos = null;

    public static void handleAbsMouseMovePacket(float x, float y, float width, float height) {
        x = x / width;
        y = y / height;
        Point point = _translate(x, y);
        if (singlePoint != null) {
            singlePoint = point;
            _handleTouchEventMove(0, singlePoint.x, singlePoint.y);
        } else {
            singlePoint = point;
        }
        cursorPos = point;
        if (showCursor) MoonlightCursorOverlay.update(point.x, point.y);
    }

    public static void handleRelMouseMovePacket(short deltaX, short deltaY) {
        if (cursorPos == null) {
            cursorPos = new Point();
            cursorPos.x = defaultDisplayHeight / 2;
            cursorPos.y = defaultDisplayWidth / 2;
        }
        cursorPos.x += deltaX;
        cursorPos.y += deltaY;
        // clamp
        cursorPos.x = Math.max(0, Math.min(cursorPos.x, defaultDisplayHeight));
        cursorPos.y = Math.max(0, Math.min(cursorPos.y, defaultDisplayWidth));

        if (singlePoint != null) {
            singlePoint = cursorPos;
            _handleTouchEventMove(0, singlePoint.x, singlePoint.y);
        }
        if (showCursor) MoonlightCursorOverlay.update(cursorPos.x, cursorPos.y);
    }

    public static void handleLeftMouseButton(boolean release) {
        if (singlePoint == null && cursorPos == null) {
            return;
        }
        if (singlePoint == null) {
            singlePoint = cursorPos;
        }
        if (release) {
            _handleTouchEventUp(0, singlePoint.x, singlePoint.y, false);
            singlePoint = null;
        } else {
            _handleTouchEventDown(0, singlePoint.x, singlePoint.y);
        }
    }

    public static void handleTouchPacket(int eventType, int rotation, int pointerId,
                                         float x, float y, float pressureOrDistance,
                                         float contactAreaMajor, float contactAreaMinor) {
        Point point = _translate(x, y);
        pointerId = pointerId % 10;
        switch (eventType) {
            case 0x01: // LI_TOUCH_EVENT_DOWN
                _handleTouchEventDown(pointerId, point.x, point.y);
                break;
            case 0x02: // LI_TOUCH_EVENT_UP
                _handleTouchEventUp(pointerId, point.x, point.y, false);
                break;
            case 0x03: // LI_TOUCH_EVENT_MOVE
                _handleTouchEventMove(pointerId, point.x, point.y);
                break;
            case 0x04: // LI_TOUCH_EVENT_CANCEL
                _handleTouchEventUp(pointerId, point.x, point.y, true);
                break;
            case 0x07: // LI_TOUCH_EVENT_CANCEL_ALL
                _handleTouchEventCancelAll();
                break;
            default:
                Log.e(TAG, "Unknown touch event type: " + eventType);
        }
    }

    private static void _handleTouchEventDown(int pointerId, float x, float y) {
        if (!bufferedMove.isEmpty()) {
            bufferedMove.clear();
            _triggerTouchEventMove();
        }

        Point point = new Point();
        point.x = x;
        point.y = y;

        boolean isFirstPointer = pointers.isEmpty();

        pointers.put(pointerId, point);

        ArrayList<Integer> pointerIds = new ArrayList<>(pointers.keySet());
        int action;
        if (isFirstPointer) {
            action = MotionEvent.ACTION_DOWN;
        } else {
            int pointerIndex = 0;
            int i = 0;
            for (Integer id : pointerIds) {
                if (id == pointerId) {
                    pointerIndex = i;
                    break;
                }
                i++;
            }
            action = MotionEvent.ACTION_POINTER_DOWN | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        }

        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointers.size()];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointers.size()];

        int index = 0;
        for (Integer k : pointerIds) {
            Point status = pointers.get(k);
            properties[index] = new MotionEvent.PointerProperties();
            properties[index].id = k;  // keep id as original pointerId
            properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

            coords[index] = new MotionEvent.PointerCoords();
            coords[index].x = status.x;
            coords[index].y = status.y;
            coords[index].pressure = 1.0f;
            index++;
        }

        MotionEvent event = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                pointers.size(),
                properties,
                coords,
                0, // metaState
                0, // buttonState
                1.0f, // xPrecision
                1.0f, // yPrecision
                0, // deviceId
                0, // edgeFlags
                InputDevice.SOURCE_TOUCHSCREEN,
                0 // flags
        );
        _injectEvent("inject down", event);
    }

    private static void _injectEvent(String prefix, MotionEvent event) {
        if (autoScale && autoRotateAndScaleForMoonlight != null) {
            autoRotateAndScaleForMoonlight.exitScale();
        }
        if (inputManager != null) {
            if (State.mirrorVirtualDisplay != null) {
                MotionEventHidden motionEventHidden = Refine.unsafeCast(event);
                motionEventHidden.setDisplayId(State.mirrorVirtualDisplay.getDisplay().getDisplayId());
            }
            try {
                inputManager.injectInputEvent(event, 0);
                Log.d(TAG, prefix + ": " + event);
                return;
            } catch (Exception e) {
                Log.w(TAG, "Shizuku inject failed, clearing inputManager", e);
                inputManager = null;
            }
        }
        // Without Shizuku, touch injection is not available.
        // Use the Display Extend app for touchpad/accessibility-based input.
    }

    private static void _handleTouchEventUp(int pointerId, float x, float y, boolean cancelled) {
        Point status = pointers.get(pointerId);
        if(status == null) {
            return;
        }
        if (!bufferedMove.isEmpty()) {
            bufferedMove.clear();
            _triggerTouchEventMove();
        }
        status.x = x;
        status.y = y;

        int pointerIndex = 0;
        int i = 0;
        ArrayList<Integer> pointerIds = new ArrayList<>(pointers.keySet());
        for (Integer id : pointerIds) {
            if (id == pointerId) {
                pointerIndex = i;
                break;
            }
            i++;
        }

        int action;
        if (pointers.size() == 1) {
            action = MotionEvent.ACTION_UP;
        } else {
            action = MotionEvent.ACTION_POINTER_UP | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        }

        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointers.size()];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointers.size()];

        int index = 0;
        for (Integer k : pointerIds) {
            Point ps = pointers.get(k);
            properties[index] = new MotionEvent.PointerProperties();
            properties[index].id = k;  // keep id as original pointerId
            properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

            coords[index] = new MotionEvent.PointerCoords();
            coords[index].x = ps.x;
            coords[index].y = ps.y;
            coords[index].pressure = k == pointerId ? 0.0f : 1.0f;
            index++;
        }

        MotionEvent event = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                pointers.size(),
                properties,
                coords,
                0, // metaState
                0, // buttonState
                1.0f, // xPrecision
                1.0f, // yPrecision
                0, // deviceId
                0, // edgeFlags
                InputDevice.SOURCE_TOUCHSCREEN,
                cancelled ? MotionEvent.FLAG_CANCELED : 0 // flags
        );

        pointers.remove(pointerId);

        _injectEvent("inject up", event);
    }

    private static Set<Integer> bufferedMove = new HashSet<>();

    private static void _handleTouchEventMove(int pointerId, float x, float y) {
        Point status = pointers.get(pointerId);
        if (status == null) {
            return;
        }

        if (bufferedMove.contains(pointerId) || bufferedMove.size() == pointers.size()) {
            bufferedMove.clear();
            _triggerTouchEventMove();
        } else {
            bufferedMove.add(pointerId);
        }

        status.x = x;
        status.y = y;
    }

    private static void _handleTouchEventCancelAll() {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();


        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointers.size()];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointers.size()];

        int index = 0;
        for (Integer k : pointers.keySet()) {
            Point status = pointers.get(k);
            properties[index] = new MotionEvent.PointerProperties();
            properties[index].id = k;
            properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

            coords[index] = new MotionEvent.PointerCoords();
            coords[index].x = status.x;
            coords[index].y = status.y;
            coords[index].pressure = 1.0f;
            index++;
        }

        MotionEvent event = MotionEvent.obtain(
                downTime,
                eventTime,
                android.view.MotionEvent.ACTION_CANCEL,
                pointers.size(),
                properties,
                coords,
                0, 0, 1.0f, 1.0f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
        );
        pointers.clear();

        _injectEvent("inject cancel", event);
    }

    private static void _triggerTouchEventMove() {
        if (pointers.isEmpty()) {
            return;
        }
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointers.size()];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointers.size()];

        int index = 0;
        for (Integer k : pointers.keySet()) {
            Point status = pointers.get(k);
            properties[index] = new MotionEvent.PointerProperties();
            properties[index].id = k;
            properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

            coords[index] = new MotionEvent.PointerCoords();
            coords[index].x = status.x;
            coords[index].y = status.y;
            coords[index].pressure = 1.0f;
            index++;
        }

        MotionEvent event = MotionEvent.obtain(
                downTime,
                eventTime,
                android.view.MotionEvent.ACTION_MOVE,
                pointers.size(),
                properties,
                coords,
                0, 0, 1.0f, 1.0f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
        );
        _injectEvent("inject move", event);
    }
}
