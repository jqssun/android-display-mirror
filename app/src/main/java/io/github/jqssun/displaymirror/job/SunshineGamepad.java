package io.github.jqssun.displaymirror.job;

import android.hardware.input.IInputManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.KeyEventHidden;
import android.view.MotionEvent;
import android.view.MotionEventHidden;
import dev.rikka.tools.refine.Refine;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

// translates a moonlight controller state into android gamepad input.
// digital buttons inject as key events (so the dpad drives ui navigation),
// while sticks and triggers inject as a joystick motion event.
public class SunshineGamepad {
  private static final String TAG = "SunshineGamepad";

  // moonlight buttonFlags, see moonlight-common-c Limelight.h
  private static final int UP = 0x0001;
  private static final int DOWN = 0x0002;
  private static final int LEFT = 0x0004;
  private static final int RIGHT = 0x0008;
  private static final int PLAY = 0x0010; // start
  private static final int BACK = 0x0020; // select
  private static final int LS_CLK = 0x0040;
  private static final int RS_CLK = 0x0080;
  private static final int LB = 0x0100;
  private static final int RB = 0x0200;
  private static final int SPECIAL = 0x0400; // guide
  private static final int A = 0x1000;
  private static final int B = 0x2000;
  private static final int X = 0x4000;
  private static final int Y = 0x8000;

  private static final int DPAD_MASK = UP | DOWN | LEFT | RIGHT;

  // {moonlight flag, android keycode}
  private static final int[][] BUTTONS = {
    {UP, KeyEvent.KEYCODE_DPAD_UP},
    {DOWN, KeyEvent.KEYCODE_DPAD_DOWN},
    {LEFT, KeyEvent.KEYCODE_DPAD_LEFT},
    {RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT},
    {A, KeyEvent.KEYCODE_BUTTON_A},
    {B, KeyEvent.KEYCODE_BUTTON_B},
    {X, KeyEvent.KEYCODE_BUTTON_X},
    {Y, KeyEvent.KEYCODE_BUTTON_Y},
    {LB, KeyEvent.KEYCODE_BUTTON_L1},
    {RB, KeyEvent.KEYCODE_BUTTON_R1},
    {LS_CLK, KeyEvent.KEYCODE_BUTTON_THUMBL},
    {RS_CLK, KeyEvent.KEYCODE_BUTTON_THUMBR},
    {PLAY, KeyEvent.KEYCODE_BUTTON_START},
    {BACK, KeyEvent.KEYCODE_BUTTON_SELECT},
    {SPECIAL, KeyEvent.KEYCODE_BUTTON_MODE},
  };

  private static IInputManager inputManager;
  private static int prevButtons = 0;

  public static void initialize() {
    prevButtons = 0;
    if (ShizukuUtils.hasPermission()) {
      inputManager = ServiceUtils.getInputManager();
    }
  }

  public static void handleGamepadState(
      int buttonFlags,
      int leftTrigger,
      int rightTrigger,
      int leftStickX,
      int leftStickY,
      int rightStickX,
      int rightStickY) {
    if (inputManager == null) {
      return;
    }
    int changed = buttonFlags ^ prevButtons;
    for (int[] b : BUTTONS) {
      if ((changed & b[0]) != 0) {
        _injectKey(b[1], (buttonFlags & b[0]) != 0, (b[0] & DPAD_MASK) != 0);
      }
    }
    prevButtons = buttonFlags;

    _injectJoystick(leftTrigger, rightTrigger, leftStickX, leftStickY, rightStickX, rightStickY);
  }

  private static void _injectKey(int keyCode, boolean down, boolean dpad) {
    long now = SystemClock.uptimeMillis();
    int source = InputDevice.SOURCE_GAMEPAD | (dpad ? InputDevice.SOURCE_DPAD : 0);
    KeyEvent event =
        new KeyEvent(
            now,
            now,
            down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
            keyCode,
            0, // repeat
            0, // metaState
            KeyCharacterMap.VIRTUAL_KEYBOARD, // deviceId
            0, // scancode
            0, // flags
            source);
    KeyEventHidden hidden = Refine.unsafeCast(event);
    hidden.setDisplayId(State.getInputDisplayId());
    _inject(event);
  }

  private static float _stick(int v) {
    return Math.max(-1f, Math.min(1f, v / 32767f));
  }

  private static float _trigger(int v) {
    return Math.max(0f, Math.min(1f, v / 255f));
  }

  private static void _injectJoystick(int lt, int rt, int lsX, int lsY, int rsX, int rsY) {
    long now = SystemClock.uptimeMillis();

    MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
    props.id = 0;
    props.toolType = MotionEvent.TOOL_TYPE_UNKNOWN;

    MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
    // moonlight stick y is positive-up, android joystick y is positive-down
    c.setAxisValue(MotionEvent.AXIS_X, _stick(lsX));
    c.setAxisValue(MotionEvent.AXIS_Y, -_stick(lsY));
    // right stick: AXIS_Z/RZ is the common mapping, RX/RY covers other controllers
    c.setAxisValue(MotionEvent.AXIS_Z, _stick(rsX));
    c.setAxisValue(MotionEvent.AXIS_RZ, -_stick(rsY));
    c.setAxisValue(MotionEvent.AXIS_RX, _stick(rsX));
    c.setAxisValue(MotionEvent.AXIS_RY, -_stick(rsY));
    // triggers: LTRIGGER/RTRIGGER plus BRAKE/GAS aliases for compatibility
    float lTrig = _trigger(lt);
    float rTrig = _trigger(rt);
    c.setAxisValue(MotionEvent.AXIS_LTRIGGER, lTrig);
    c.setAxisValue(MotionEvent.AXIS_RTRIGGER, rTrig);
    c.setAxisValue(MotionEvent.AXIS_BRAKE, lTrig);
    c.setAxisValue(MotionEvent.AXIS_GAS, rTrig);

    MotionEvent event =
        MotionEvent.obtain(
            now,
            now,
            MotionEvent.ACTION_MOVE,
            1,
            new MotionEvent.PointerProperties[] {props},
            new MotionEvent.PointerCoords[] {c},
            0, // metaState
            0, // buttonState
            1.0f, // xPrecision
            1.0f, // yPrecision
            0, // deviceId
            0, // edgeFlags
            InputDevice.SOURCE_JOYSTICK | InputDevice.SOURCE_GAMEPAD,
            0 // flags
            );
    MotionEventHidden hidden = Refine.unsafeCast(event);
    hidden.setDisplayId(State.getInputDisplayId());
    _inject(event);
  }

  private static void _inject(InputEvent event) {
    try {
      inputManager.injectInputEvent(event, 0);
      Log.d(TAG, "inject: " + event);
    } catch (Exception e) {
      Log.w(TAG, "Shizuku inject failed, clearing inputManager", e);
      inputManager = null;
    }
  }
}
