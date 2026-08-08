package io.github.jqssun.displaymirror.sunshine;

import android.annotation.SuppressLint;
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
import dev.rikka.tools.refine.Refine;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.ProjectionService;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.job.CreateVirtualDisplay;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SunshineMouse {
  private static String TAG = "SunshineMouse";
  private static IInputManager inputManager;

  // per-client coordinate mapping
  private static class Geometry {
    float defaultDisplayWidth;
    float defaultDisplayHeight;
    // screenWidth * screenHeight always in landscape mode
    float screenWidth;
    float screenHeight;
    float portraitMirrorWidth;
    float portraitMirrorHeight;
    float landscapeMirrorWidth;
    float landscapeMirrorHeight;
  }

  private static final Map<Long, Geometry> geometries = new ConcurrentHashMap<>();

  private static boolean cropBlackBorders;
  private static boolean rotateWithContent;
  private static boolean showCursor;

  public static void setShowCursor(boolean show) {
    showCursor = show;
    if (show) SunshineCursorOverlay.show();
    else SunshineCursorOverlay.hide();
  }

  public static void initialize(long session, int width, int height) {
    Context context = State.getContext();
    if (context == null) {
      return;
    }
    if (ShizukuUtils.hasPermission()) {
      inputManager = ServiceUtils.getInputManager();
    }
    Geometry g = new Geometry();
    g.screenWidth = width;
    g.screenHeight = height;
    rotateWithContent = Pref.getRotateWithContent();
    cropBlackBorders = Pref.getCropBlackBorders();
    showCursor = Pref.getSunshineShowCursor();
    if (showCursor) {
      SunshineCursorOverlay.show();
    }

    DisplayManager displayManager =
        (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
    boolean firstClient = SunshineState.virtualDisplay == null && SunshineState.pipeline == null;
    if (firstClient
        && Pref.getAutoMatchAspectRatio()
        && ShizukuUtils.hasPermission()
        && CreateVirtualDisplay.streamMirrors()) {
      // first client wins forced aspect
      CreateVirtualDisplay.changeAspectRatio(width, height);
      IWindowManager windowManager = ServiceUtils.getWindowManager();
      android.graphics.Point baseSize = new android.graphics.Point();
      windowManager.getBaseDisplaySize(Display.DEFAULT_DISPLAY, baseSize);
      g.defaultDisplayWidth = Math.max(baseSize.x, baseSize.y);
      g.defaultDisplayHeight = Math.min(baseSize.x, baseSize.y);
      float aspectRatio1 = g.defaultDisplayWidth / g.defaultDisplayHeight;
      float aspectRatio2 = g.screenWidth / g.screenHeight;
      if (Math.abs(aspectRatio1 - aspectRatio2) > 0.01) {
        // change resolution to avoid stretching
        g.defaultDisplayWidth = g.screenWidth;
        DisplayCutout cutout = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
          cutout = defaultDisplay.getCutout();
        }
        if (cutout != null) {
          _applyCutoutWidth(g, cutout);
        }
      }
    } else {
      android.graphics.Point realSize = new android.graphics.Point();
      defaultDisplay.getRealSize(realSize);
      g.defaultDisplayWidth = Math.max(realSize.x, realSize.y);
      g.defaultDisplayHeight = Math.min(realSize.x, realSize.y);
    }
    float aspectRatio = g.defaultDisplayWidth / g.defaultDisplayHeight;

    g.landscapeMirrorHeight = g.screenHeight;
    g.landscapeMirrorWidth = g.landscapeMirrorHeight * aspectRatio;
    if (g.landscapeMirrorWidth > g.screenWidth) {
      g.landscapeMirrorWidth = g.screenWidth;
      g.landscapeMirrorHeight = g.landscapeMirrorWidth / aspectRatio;
    }

    g.portraitMirrorHeight = g.screenHeight;
    g.portraitMirrorWidth = g.portraitMirrorHeight / aspectRatio;
    if (g.portraitMirrorWidth > g.screenWidth) {
      g.portraitMirrorWidth = g.screenWidth;
      g.portraitMirrorHeight = g.portraitMirrorWidth * aspectRatio;
    }

    geometries.put(session, g);

    State.log(
        "primary display size defaultDisplayWidth: "
            + g.defaultDisplayWidth
            + " defaultDisplayHeight: "
            + g.defaultDisplayHeight);
    State.log(
        "client screen size screenWidth: " + g.screenWidth + " screenHeight: " + g.screenHeight);
    if (firstClient) {
      State.log(
          "mirror mode portraitMirrorWidth: "
              + g.portraitMirrorWidth
              + " portraitMirrorHeight: "
              + g.portraitMirrorHeight
              + " landscapeMirrorWidth: "
              + g.landscapeMirrorWidth
              + " landscapeMirrorHeight: "
              + g.landscapeMirrorHeight);
    }
  }

  public static void removeSession(long session) {
    geometries.remove(session);
  }

  private static Geometry _geometry(long session) {
    Geometry g = geometries.get(session);
    if (g != null) {
      return g;
    }
    // input can race session registration
    Iterator<Geometry> it = geometries.values().iterator();
    return it.hasNext() ? it.next() : null;
  }

  @SuppressLint("NewApi")
  private static void _applyCutoutWidth(Geometry g, DisplayCutout cutout) {
    for (Rect rect : cutout.getBoundingRects()) {
      if (rect.top == 0) {
        g.defaultDisplayWidth += rect.bottom * 2;
        break;
      }
    }
  }

  private static class Point {
    public float x = 0;
    public float y = 0;
  }

  private static Map<Integer, Point> pointers = new HashMap<>();

  private static Point _translate(Geometry g, float x, float y) {
    if (SunshineState.inputToExternalDisplay()) {
      return _translateVirtualDisplay(g, x, y);
    } else {
      return _translateMirrorMode(g, x, y);
    }
  }

  private static Point _translateMirrorMode(Geometry g, float x, float y) {
    boolean isLandscape =
        ProjectionService.instance.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE;
    float xInScreen = x * g.screenWidth;
    float yInScreen = y * g.screenHeight;
    if (isLandscape) {
      return _translateRotation90Mirror(g, xInScreen, yInScreen);
    } else {
      return _translateRotation0Mirror(g, xInScreen, yInScreen);
    }
  }

  private static Point _translateRotation0Mirror(Geometry g, float xInScreen, float yInScreen) {
    if (rotateWithContent) {
      Point point = new Point();
      float xBlackBar = (g.screenWidth - g.landscapeMirrorWidth) / 2;
      float yBlackBar = (g.screenHeight - g.landscapeMirrorHeight) / 2;
      float adjustedX = xInScreen - xBlackBar;
      if (adjustedX > g.landscapeMirrorWidth) {
        adjustedX = g.landscapeMirrorWidth;
      } else if (adjustedX < 0) {
        adjustedX = 0;
      }
      float adjustedY = yInScreen - yBlackBar;
      if (adjustedY > g.landscapeMirrorHeight) {
        adjustedY = g.landscapeMirrorHeight;
      } else if (adjustedY < 0) {
        adjustedY = 0;
      }
      point.y = (adjustedX / g.landscapeMirrorWidth) * g.defaultDisplayWidth;
      point.x = (1 - (adjustedY / g.landscapeMirrorHeight)) * g.defaultDisplayHeight;
      return point;
    } else {
      Point point = new Point();
      float xBlackBar = (g.screenWidth - g.portraitMirrorWidth) / 2;
      float yBlackBar = (g.screenHeight - g.portraitMirrorHeight) / 2;
      float adjustedX = xInScreen - xBlackBar;
      if (adjustedX > g.portraitMirrorWidth) {
        adjustedX = g.portraitMirrorWidth;
      } else if (adjustedX < 0) {
        adjustedX = 0;
      }
      float adjustedY = yInScreen - yBlackBar;
      if (adjustedY > g.portraitMirrorHeight) {
        adjustedY = g.portraitMirrorHeight;
      } else if (adjustedY < 0) {
        adjustedY = 0;
      }
      point.x = (adjustedX / g.portraitMirrorWidth) * g.defaultDisplayHeight;
      point.y = (adjustedY / g.portraitMirrorHeight) * g.defaultDisplayWidth;
      return point;
    }
  }

  private static Point _translateRotation90Mirror(Geometry g, float xInScreen, float yInScreen) {
    Point point = new Point();
    float xBlackBar = (g.screenWidth - g.landscapeMirrorWidth) / 2;
    float yBlackBar = (g.screenHeight - g.landscapeMirrorHeight) / 2;
    float adjustedX = xInScreen - xBlackBar;
    if (adjustedX > g.landscapeMirrorWidth) {
      adjustedX = g.landscapeMirrorWidth;
    } else if (adjustedX < 0) {
      adjustedX = 0;
    }
    float adjustedY = yInScreen - yBlackBar;
    if (adjustedY > g.landscapeMirrorHeight) {
      adjustedY = g.landscapeMirrorHeight;
    } else if (adjustedY < 0) {
      adjustedY = 0;
    }
    point.x = (adjustedX / g.landscapeMirrorWidth) * g.defaultDisplayWidth;
    point.y = (adjustedY / g.landscapeMirrorHeight) * g.defaultDisplayHeight;
    return point;
  }

  private static @NonNull Point _translateVirtualDisplay(Geometry g, float x, float y) {
    int displayRotation = SunshineState.virtualDisplay.getDisplay().getRotation();
    Point point = new Point();
    switch (displayRotation) {
      case Surface.ROTATION_0:
        point.x = x * g.screenWidth;
        point.y = y * g.screenHeight;
        break;
      case Surface.ROTATION_90:
        point.x = y * g.screenHeight;
        point.y = (1 - x) * g.screenWidth;
        break;
      case Surface.ROTATION_180:
        point.x = (1 - x) * g.screenWidth;
        point.y = (1 - y) * g.screenHeight;
        break;
      case Surface.ROTATION_270:
        point.x = (1 - y) * g.screenHeight;
        point.y = x * g.screenWidth;
        break;
    }
    return point;
  }

  private static Point singlePoint = null;
  // cursor position in normalized 0-1 coordinates for relative mouse
  private static Point cursorPos = null;

  public static void handleAbsMouseMovePacket(long session, float x, float y, float width, float height) {
    Geometry g = _geometry(session);
    if (g == null) {
      return;
    }
    x = x / width;
    y = y / height;
    if (cursorPos == null) cursorPos = new Point();
    cursorPos.x = x;
    cursorPos.y = y;
    Point point = _translate(g, x, y);
    if (singlePoint != null) {
      singlePoint = point;
      _handleTouchEventMove(0, singlePoint.x, singlePoint.y);
    } else {
      singlePoint = point;
    }
    if (showCursor) {
      Point cursorPoint = _translateMirrorMode(g, x, y);
      SunshineCursorOverlay.update(cursorPoint.x, cursorPoint.y);
    }
  }

  public static void handleRelMouseMovePacket(long session, short deltaX, short deltaY) {
    Geometry g = _geometry(session);
    if (g == null) {
      return;
    }
    if (cursorPos == null) {
      cursorPos = new Point();
      cursorPos.x = 0.5f;
      cursorPos.y = 0.5f;
    }
    cursorPos.x += deltaX / g.screenWidth;
    cursorPos.y += deltaY / g.screenHeight;
    cursorPos.x = Math.max(0, Math.min(cursorPos.x, 1));
    cursorPos.y = Math.max(0, Math.min(cursorPos.y, 1));

    Point injPoint = _translate(g, cursorPos.x, cursorPos.y);
    if (singlePoint != null) {
      singlePoint = injPoint;
      _handleTouchEventMove(0, singlePoint.x, singlePoint.y);
    }
    if (showCursor) {
      Point cursorScreenPoint = _translateMirrorMode(g, cursorPos.x, cursorPos.y);
      SunshineCursorOverlay.update(cursorScreenPoint.x, cursorScreenPoint.y);
    }
  }

  public static void handleLeftMouseButton(long session, boolean release) {
    Geometry g = _geometry(session);
    if (g == null || cursorPos == null) {
      return;
    }
    Point injPoint = _translate(g, cursorPos.x, cursorPos.y);
    if (release) {
      _handleTouchEventUp(0, injPoint.x, injPoint.y, false);
      singlePoint = null;
    } else {
      singlePoint = injPoint;
      _handleTouchEventDown(0, injPoint.x, injPoint.y);
    }
  }

  public static void handleTouchPacket(
      long session,
      int eventType,
      int rotation,
      int pointerId,
      float x,
      float y,
      float pressureOrDistance,
      float contactAreaMajor,
      float contactAreaMinor) {
    Geometry g = _geometry(session);
    if (g == null) {
      return;
    }
    Point point = _translate(g, x, y);
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
      action =
          MotionEvent.ACTION_POINTER_DOWN
              | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    }

    long downTime = SystemClock.uptimeMillis();
    long eventTime = SystemClock.uptimeMillis();

    MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointers.size()];
    MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointers.size()];

    int index = 0;
    for (Integer k : pointerIds) {
      Point status = pointers.get(k);
      properties[index] = new MotionEvent.PointerProperties();
      properties[index].id = k; // keep id as original pointerId
      properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

      coords[index] = new MotionEvent.PointerCoords();
      coords[index].x = status.x;
      coords[index].y = status.y;
      coords[index].pressure = 1.0f;
      index++;
    }

    MotionEvent event =
        MotionEvent.obtain(
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
    if (cropBlackBorders && SunshineState.pipeline != null) {
      SunshineState.pipeline.exitCrop();
    }
    if (inputManager != null) {
      MotionEventHidden motionEventHidden = Refine.unsafeCast(event);
      motionEventHidden.setDisplayId(SunshineState.getInputDisplayId());
      try {
        inputManager.injectInputEvent(event, 0);
        Log.d(TAG, prefix + ": " + event);
        return;
      } catch (Exception e) {
        Log.w(TAG, "Shizuku inject failed, clearing inputManager", e);
        inputManager = null;
      }
    }
    // without Shizuku, touch injection is not available.
    // use the Display Extend app for touchpad/accessibility-based input.
  }

  private static void _handleTouchEventUp(int pointerId, float x, float y, boolean cancelled) {
    Point status = pointers.get(pointerId);
    if (status == null) {
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
      action =
          MotionEvent.ACTION_POINTER_UP | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    }

    long downTime = SystemClock.uptimeMillis();
    long eventTime = SystemClock.uptimeMillis();

    MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointers.size()];
    MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointers.size()];

    int index = 0;
    for (Integer k : pointerIds) {
      Point ps = pointers.get(k);
      properties[index] = new MotionEvent.PointerProperties();
      properties[index].id = k; // keep id as original pointerId
      properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

      coords[index] = new MotionEvent.PointerCoords();
      coords[index].x = ps.x;
      coords[index].y = ps.y;
      coords[index].pressure = k == pointerId ? 0.0f : 1.0f;
      index++;
    }

    MotionEvent event =
        MotionEvent.obtain(
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

    MotionEvent event =
        MotionEvent.obtain(
            downTime,
            eventTime,
            android.view.MotionEvent.ACTION_CANCEL,
            pointers.size(),
            properties,
            coords,
            0,
            0,
            1.0f,
            1.0f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0);
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

    MotionEvent event =
        MotionEvent.obtain(
            downTime,
            eventTime,
            android.view.MotionEvent.ACTION_MOVE,
            pointers.size(),
            properties,
            coords,
            0,
            0,
            1.0f,
            1.0f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0);
    _injectEvent("inject move", event);
  }
}
