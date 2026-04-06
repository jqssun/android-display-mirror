package io.github.jqssun.displaymirror.job;

import android.content.Context;
import android.hardware.input.IInputManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.KeyEventHidden;
import android.view.MotionEventHidden;

import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.TouchpadAccessibilityService;
import io.github.jqssun.displaymirror.TouchpadActivity;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import dev.rikka.tools.refine.Refine;

public class SunshineKeyboard {
    private static String TAG = "SunshineKeyboard";

    /**
     * GFE's prefix for every key code
     */
    private static final short KEY_PREFIX = (short) 0x80;

    public static final int VK_0 = 48;
    public static final int VK_9 = 57;
    public static final int VK_A = 65;
    public static final int VK_Z = 90;
    public static final int VK_NUMPAD0 = 96;
    public static final int VK_BACK_SLASH = 92;
    public static final int VK_CAPS_LOCK = 20;
    public static final int VK_CLEAR = 12;
    public static final int VK_COMMA = 44;
    public static final int VK_BACK_SPACE = 8;
    public static final int VK_EQUALS = 61;
    public static final int VK_ESCAPE = 27;
    public static final int VK_F1 = 112;
    public static final int VK_F12 = 123;

    public static final int VK_END = 35;
    public static final int VK_HOME = 36;
    public static final int VK_NUM_LOCK = 144;
    public static final int VK_PAGE_UP = 33;
    public static final int VK_PAGE_DOWN = 34;
    public static final int VK_PLUS = 521;
    public static final int VK_CLOSE_BRACKET = 93;
    public static final int VK_SCROLL_LOCK = 145;
    public static final int VK_SEMICOLON = 59;
    public static final int VK_SLASH = 47;
    public static final int VK_SPACE = 32;
    public static final int VK_PRINTSCREEN = 154;
    public static final int VK_TAB = 9;
    public static final int VK_LEFT = 37;
    public static final int VK_RIGHT = 39;
    public static final int VK_UP = 38;
    public static final int VK_DOWN = 40;
    public static final int VK_BACK_QUOTE = 192;
    public static final int VK_QUOTE = 222;
    public static final int VK_PAUSE = 19;

    public static final int VK_B = 66;

    public static final int VK_C = 67;
    public static final int VK_D = 68;
    public static final int VK_G = 71;
    public static final int VK_V = 86;
    public static final int VK_Q = 81;

    public static final int VK_S = 83;

    public static final int VK_U = 85;

    public static final int VK_X = 88;
    public static final int VK_R = 82;

    public static final int VK_I = 73;

    public static final int VK_F11 = 122;
    public static final int VK_LWIN = 91;
    public static final int VK_LSHIFT = 160;
    public static final int VK_LCONTROL = 162;

    //Left ALT key
    public static final int VK_LMENU = 164;
    //ENTER key
    public static final int VK_RETURN = 13;

    public static final int VK_F4 = 115;

    public static final int VK_P = 80;

    public static final byte MODIFIER_SHIFT = 0x01;
    public static final byte MODIFIER_CTRL = 0x02;
    public static final byte MODIFIER_ALT = 0x04;
    public static final byte MODIFIER_META = 0x08;
    private static IInputManager inputManager;
    private static boolean singleAppMode;

    private static int currentMetaState = 0;
    
    private static void updateMetaState(int keycode, boolean pressed) {
        int mask = 0;
        switch (keycode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
                mask = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                mask = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_CTRL_LEFT:
                mask = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                mask = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_ALT_LEFT:
                mask = KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_ALT_RIGHT:
                mask = KeyEvent.META_ALT_ON | KeyEvent.META_ALT_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_META_LEFT:
                mask = KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_META_RIGHT:
                mask = KeyEvent.META_META_ON | KeyEvent.META_META_RIGHT_ON;
                break;
        }
        
        if (pressed) {
            currentMetaState |= mask;
        } else {
            currentMetaState &= ~mask;
        }
    }

    public static void initialize() {
        Context context = State.getContext();
        if (context == null) {
            return;
        }
        if (ShizukuUtils.hasPermission()) {
            inputManager = ServiceUtils.getInputManager();
        }
        singleAppMode = Pref.getSingleAppMode();
    }

    public static void handleKeyboardEvent(int modcode, boolean release, int _notUsed) {
        if(inputManager == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        int androidKeyCode = translateWindowsVKToAndroidKey(modcode);
        
        updateMetaState(androidKeyCode, !release);

        KeyEvent keyEvent = new KeyEvent(now, now,
                release ? KeyEvent.ACTION_UP : KeyEvent.ACTION_DOWN,
                androidKeyCode, 0, currentMetaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                InputDevice.SOURCE_KEYBOARD);
        if (singleAppMode) {
            if (State.mirrorVirtualDisplay == null) {
                return;
            }
            KeyEventHidden keyEventHidden = Refine.unsafeCast(keyEvent);
            keyEventHidden.setDisplayId(State.mirrorVirtualDisplay.getDisplay().getDisplayId());
        }
        Log.d(TAG, "handleKeyboardEvent: " + modcode + " translated to " + keyEvent);
        inputManager.injectInputEvent(keyEvent, 0);
    }


    /**
     * Translates the given keycode and returns the GFE keycode
     * @param keycode the code to be translated
     * @return a GFE keycode for the given keycode
     */
    private static short translateAndroidKeyToWindowsVK(int keycode) {
        int translated;

        // This is a poor man's mapping between Android key codes
        // and Windows VK_* codes. For all defined VK_ codes, see:
        // https://msdn.microsoft.com/en-us/library/windows/desktop/dd375731(v=vs.85).aspx
        if (keycode >= KeyEvent.KEYCODE_0 &&
                keycode <= KeyEvent.KEYCODE_9) {
            translated = (keycode - KeyEvent.KEYCODE_0) + VK_0;
        }
        else if (keycode >= KeyEvent.KEYCODE_A &&
                keycode <= KeyEvent.KEYCODE_Z) {
            translated = (keycode - KeyEvent.KEYCODE_A) + VK_A;
        }
        else if (keycode >= KeyEvent.KEYCODE_NUMPAD_0 &&
                keycode <= KeyEvent.KEYCODE_NUMPAD_9) {
            translated = (keycode - KeyEvent.KEYCODE_NUMPAD_0) + VK_NUMPAD0;
        }
        else if (keycode >= KeyEvent.KEYCODE_F1 &&
                keycode <= KeyEvent.KEYCODE_F12) {
            translated = (keycode - KeyEvent.KEYCODE_F1) + VK_F1;
        }
        else {
            switch (keycode) {
                case KeyEvent.KEYCODE_ALT_LEFT:
                    translated = 0xA4;
                    break;

                case KeyEvent.KEYCODE_ALT_RIGHT:
                    translated = 0xA5;
                    break;

                case KeyEvent.KEYCODE_BACKSLASH:
                    translated = 0xdc;
                    break;

                case KeyEvent.KEYCODE_CAPS_LOCK:
                    translated = VK_CAPS_LOCK;
                    break;

                case KeyEvent.KEYCODE_CLEAR:
                    translated = VK_CLEAR;
                    break;

                case KeyEvent.KEYCODE_COMMA:
                    translated = 0xbc;
                    break;

                case KeyEvent.KEYCODE_CTRL_LEFT:
                    translated = 0xA2;
                    break;

                case KeyEvent.KEYCODE_CTRL_RIGHT:
                    translated = 0xA3;
                    break;

                case KeyEvent.KEYCODE_DEL:
                    translated = VK_BACK_SPACE;
                    break;

                case KeyEvent.KEYCODE_ENTER:
                    translated = 0x0d;
                    break;

                case KeyEvent.KEYCODE_PLUS:
                case KeyEvent.KEYCODE_EQUALS:
                    translated = 0xbb;
                    break;

                case KeyEvent.KEYCODE_ESCAPE:
                    translated = VK_ESCAPE;
                    break;

                case KeyEvent.KEYCODE_FORWARD_DEL:
                    translated = 0x2e;
                    break;

                case KeyEvent.KEYCODE_INSERT:
                    translated = 0x2d;
                    break;

                case KeyEvent.KEYCODE_LEFT_BRACKET:
                    translated = 0xdb;
                    break;

                case KeyEvent.KEYCODE_META_LEFT:
                    translated = 0x5b;
                    break;

                case KeyEvent.KEYCODE_META_RIGHT:
                    translated = 0x5c;
                    break;

                case KeyEvent.KEYCODE_MENU:
                    translated = 0x5d;
                    break;

                case KeyEvent.KEYCODE_MINUS:
                    translated = 0xbd;
                    break;

                case KeyEvent.KEYCODE_MOVE_END:
                    translated = VK_END;
                    break;

                case KeyEvent.KEYCODE_MOVE_HOME:
                    translated = VK_HOME;
                    break;

                case KeyEvent.KEYCODE_NUM_LOCK:
                    translated = VK_NUM_LOCK;
                    break;

                case KeyEvent.KEYCODE_PAGE_DOWN:
                    translated = VK_PAGE_DOWN;
                    break;

                case KeyEvent.KEYCODE_PAGE_UP:
                    translated = VK_PAGE_UP;
                    break;

                case KeyEvent.KEYCODE_PERIOD:
                    translated = 0xbe;
                    break;

                case KeyEvent.KEYCODE_RIGHT_BRACKET:
                    translated = 0xdd;
                    break;

                case KeyEvent.KEYCODE_SCROLL_LOCK:
                    translated = VK_SCROLL_LOCK;
                    break;

                case KeyEvent.KEYCODE_SEMICOLON:
                    translated = 0xba;
                    break;

                case KeyEvent.KEYCODE_SHIFT_LEFT:
                    translated = 0xA0;
                    break;

                case KeyEvent.KEYCODE_SHIFT_RIGHT:
                    translated = 0xA1;
                    break;

                case KeyEvent.KEYCODE_SLASH:
                    translated = 0xbf;
                    break;

                case KeyEvent.KEYCODE_SPACE:
                    translated = VK_SPACE;
                    break;

                case KeyEvent.KEYCODE_SYSRQ:
                    // Android defines this as SysRq/PrntScrn
                    translated = VK_PRINTSCREEN;
                    break;

                case KeyEvent.KEYCODE_TAB:
                    translated = VK_TAB;
                    break;

                case KeyEvent.KEYCODE_DPAD_LEFT:
                    translated = VK_LEFT;
                    break;

                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    translated = VK_RIGHT;
                    break;

                case KeyEvent.KEYCODE_DPAD_UP:
                    translated = VK_UP;
                    break;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                    translated = VK_DOWN;
                    break;

                case KeyEvent.KEYCODE_GRAVE:
                    translated = VK_BACK_QUOTE;
                    break;

                case KeyEvent.KEYCODE_APOSTROPHE:
                    translated = 0xde;
                    break;

                case KeyEvent.KEYCODE_BREAK:
                    translated = VK_PAUSE;
                    break;

                case KeyEvent.KEYCODE_NUMPAD_DIVIDE:
                    translated = 0x6F;
                    break;

                case KeyEvent.KEYCODE_NUMPAD_MULTIPLY:
                    translated = 0x6A;
                    break;

                case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                    translated = 0x6D;
                    break;

                case KeyEvent.KEYCODE_NUMPAD_ADD:
                    translated = 0x6B;
                    break;

                case KeyEvent.KEYCODE_NUMPAD_DOT:
                    translated = 0x6E;
                    break;

                default:
                    return 0;
            }
        }

        return (short) ((KEY_PREFIX << 8) | translated);
    }


    private static int translateWindowsVKToAndroidKey(int keycode) {
        int windowsKey = keycode & 0xFF;

        if (windowsKey >= VK_0 && windowsKey <= VK_9) {
            return KeyEvent.KEYCODE_0 + (windowsKey - VK_0);
        }
        else if (windowsKey >= VK_A && windowsKey <= VK_Z) {
            return KeyEvent.KEYCODE_A + (windowsKey - VK_A);
        }
        else if (windowsKey >= VK_NUMPAD0 && windowsKey <= (VK_NUMPAD0 + 9)) {
            return KeyEvent.KEYCODE_NUMPAD_0 + (windowsKey - VK_NUMPAD0);
        }
        else if (windowsKey >= VK_F1 && windowsKey <= VK_F12) {
            return KeyEvent.KEYCODE_F1 + (windowsKey - VK_F1);
        }

        switch (windowsKey) {
            case 0xA4: return KeyEvent.KEYCODE_ALT_LEFT;
            case 0xA5: return KeyEvent.KEYCODE_ALT_RIGHT;
            case 0xDC: return KeyEvent.KEYCODE_BACKSLASH;
            case VK_CAPS_LOCK: return KeyEvent.KEYCODE_CAPS_LOCK;
            case VK_CLEAR: return KeyEvent.KEYCODE_CLEAR;
            case 0xBC: return KeyEvent.KEYCODE_COMMA;
            case 0xA2: return KeyEvent.KEYCODE_CTRL_LEFT;
            case 0xA3: return KeyEvent.KEYCODE_CTRL_RIGHT;
            case VK_BACK_SPACE: return KeyEvent.KEYCODE_DEL;
            case 0x0D: return KeyEvent.KEYCODE_ENTER;
            case 0xBB: return KeyEvent.KEYCODE_EQUALS;
            case VK_ESCAPE: return KeyEvent.KEYCODE_ESCAPE;
            case 0x2E: return KeyEvent.KEYCODE_FORWARD_DEL;
            case 0x2D: return KeyEvent.KEYCODE_INSERT;
            case 0xDB: return KeyEvent.KEYCODE_LEFT_BRACKET;
            case 0x5B: return KeyEvent.KEYCODE_META_LEFT;
            case 0x5C: return KeyEvent.KEYCODE_META_RIGHT;
            case 0x5D: return KeyEvent.KEYCODE_MENU;
            case 0xBD: return KeyEvent.KEYCODE_MINUS;
            case VK_END: return KeyEvent.KEYCODE_MOVE_END;
            case VK_HOME: return KeyEvent.KEYCODE_MOVE_HOME;
            case VK_NUM_LOCK: return KeyEvent.KEYCODE_NUM_LOCK;
            case VK_PAGE_DOWN: return KeyEvent.KEYCODE_PAGE_DOWN;
            case VK_PAGE_UP: return KeyEvent.KEYCODE_PAGE_UP;
            case 0xBE: return KeyEvent.KEYCODE_PERIOD;
            case 0xDD: return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case VK_SCROLL_LOCK: return KeyEvent.KEYCODE_SCROLL_LOCK;
            case 0xBA: return KeyEvent.KEYCODE_SEMICOLON;
            case 0xA0: return KeyEvent.KEYCODE_SHIFT_LEFT;
            case 0xA1: return KeyEvent.KEYCODE_SHIFT_RIGHT;
            case 0xBF: return KeyEvent.KEYCODE_SLASH;
            case VK_SPACE: return KeyEvent.KEYCODE_SPACE;
            case VK_PRINTSCREEN: return KeyEvent.KEYCODE_SYSRQ;
            case VK_TAB: return KeyEvent.KEYCODE_TAB;
            case VK_LEFT: return KeyEvent.KEYCODE_DPAD_LEFT;
            case VK_RIGHT: return KeyEvent.KEYCODE_DPAD_RIGHT;
            case VK_UP: return KeyEvent.KEYCODE_DPAD_UP;
            case VK_DOWN: return KeyEvent.KEYCODE_DPAD_DOWN;
            case VK_BACK_QUOTE: return KeyEvent.KEYCODE_GRAVE;
            case 0xDE: return KeyEvent.KEYCODE_APOSTROPHE;
            case VK_PAUSE: return KeyEvent.KEYCODE_BREAK;
            case 0x6F: return KeyEvent.KEYCODE_NUMPAD_DIVIDE;
            case 0x6A: return KeyEvent.KEYCODE_NUMPAD_MULTIPLY;
            case 0x6D: return KeyEvent.KEYCODE_NUMPAD_SUBTRACT;
            case 0x6B: return KeyEvent.KEYCODE_NUMPAD_ADD;
            case 0x6E: return KeyEvent.KEYCODE_NUMPAD_DOT;
            default: return 0;
        }
    }
}
