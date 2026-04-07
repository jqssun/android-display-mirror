package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.SharedPreferences;

public class Pref {
    public static final String KEY_AUTO_ROTATE = "auto_rotate";
    public static final String KEY_AUTO_SCALE = "auto_scale";
    public static final String KEY_SINGLE_APP_MODE = "single_app_mode";
    public static final String KEY_SELECTED_APP_PACKAGE = "selected_app_package";
    public static final String KEY_SELECTED_APP_NAME = "selected_app_name";
    public static final String KEY_SINGLE_APP_DPI = "single_app_dpi";
    public static final String KEY_AUTO_HIDE_FLOATING_BACK_BUTTON = "floating_back_button";
    public static final String KEY_AUTO_SCREEN_OFF = "auto_screen_off";
    public static final String KEY_AUTO_BIND_INPUT = "auto_bind_input";
    public static final String KEY_AUTO_MOVE_IME = "auto_move_ime";
    public static final String KEY_DISABLE_USB_AUDIO = "disable_usb_audio";
    public static final String KEY_USE_TOUCHSCREEN = "use_touchscreen";
    public static final String KEY_AUTO_MATCH_ASPECT_RATIO = "auto_match_aspect_ratio";
    public static final String KEY_SHOW_FLOATING_IN_MIRROR_MODE = "floating_back_button_in_mirror";
    public static final String KEY_DISPLAYLINK_WIDTH = "displaylink_width";
    public static final String KEY_DISPLAYLINK_HEIGHT = "displaylink_height";
    public static final String KEY_DISPLAYLINK_REFRESH_RATE = "displaylink_refresh_rate";
    public static final String KEY_SELECTED_CLIENT = "selected_client";
    public static final String KEY_AUTO_CONNECT_CLIENT = "auto_connect_client";
    public static final String KEY_DISABLE_ACCESSIBILITY = "disable_accessibility";
    public static final String KEY_USE_BLACK_IMAGE = "use_black_image";
    public static final String KEY_PREVENT_AUTO_LOCK = "prevent_auto_lock";
    public static final String KEY_DISABLE_REMOTE_SUBMIX = "disable_remote_submix";
    public static final String KEY_DISPLAYLINK_APK_URL = "displaylink_apk_url";
    public static final String DEFAULT_DISPLAYLINK_APK_URL = "https://www.synaptics.com/sites/default/files/exe_files/2024-12/DisplayLink%C2%AE%20USB%20Graphics%20Software%20for%20Android%204.2.0-EXE.apk";
    public static boolean doNotAutoStartMoonlight;

    public static boolean getAutoRotate() {
        return getBoolean(KEY_AUTO_ROTATE, true);
    }

    public static boolean  getAutoScale() {
        return getBoolean(KEY_AUTO_SCALE, true);
    }

    public static boolean getSingleAppMode() {
        return getBoolean(KEY_SINGLE_APP_MODE, false);
    }

    public static boolean getAutoMoveIme() {
        return getBoolean(KEY_AUTO_MOVE_IME, true);
    }

    public static boolean getAutoHideFloatingBackButton() {
        return getBoolean(KEY_AUTO_HIDE_FLOATING_BACK_BUTTON, false);
    }

    public static boolean getAutoScreenOff() {
        return getBoolean(KEY_AUTO_SCREEN_OFF, false);
    }

    public static boolean getAutoBindInput() {
        return getBoolean(KEY_AUTO_BIND_INPUT, true);
    }

    public static boolean getDisableUsbAudio() {
        return getBoolean(KEY_DISABLE_USB_AUDIO, false);
    }

    public static boolean getAutoMatchAspectRatio() {
        return getBoolean(KEY_AUTO_MATCH_ASPECT_RATIO, false);
    }

    public static boolean getUseTouchscreen() {
        return getBoolean(KEY_USE_TOUCHSCREEN, true);
    }

    public static boolean getShowFloatingInMirrorMode() {
        return getBoolean(KEY_SHOW_FLOATING_IN_MIRROR_MODE, false);
    }

    public static boolean getAutoConnectClient() {
        return getBoolean(KEY_AUTO_CONNECT_CLIENT, false);
    }

    public static int getSingleAppDpi() {
        return getInt(KEY_SINGLE_APP_DPI, 160);
    }

    public static int getDisplaylinkWidth() {
        return getInt(KEY_DISPLAYLINK_WIDTH, 1920);
    }

    public static int getDisplaylinkHeight() {
        return getInt(KEY_DISPLAYLINK_HEIGHT, 1080);
    }

    public static int getDisplaylinkRefreshRate() {
        return getInt(KEY_DISPLAYLINK_REFRESH_RATE, 60);
    }

    public static String getSelectedAppPackage() {
        return getString(KEY_SELECTED_APP_PACKAGE, "");
    }

    public static String getSelectedClient() {
        return getString(KEY_SELECTED_CLIENT, "");
    }

    public static boolean getDisableAccessibility() {
        return getBoolean(KEY_DISABLE_ACCESSIBILITY, false);
    }

    public static boolean getUseBlackImage() {
        return getBoolean(KEY_USE_BLACK_IMAGE, false);
    }

    public static boolean getPreventAutoLock() {
        return getBoolean(KEY_PREVENT_AUTO_LOCK, false);
    }

    public static boolean getDisableRemoteSubmix() {
        return getBoolean(KEY_DISABLE_REMOTE_SUBMIX, false);
    }

    public static String getDisplaylinkApkUrl() {
        return getString(KEY_DISPLAYLINK_APK_URL, DEFAULT_DISPLAYLINK_APK_URL);
    }

    private static String getString(String key, String defaultValue) {
        SharedPreferences preferences = getPreferences();
        if (preferences == null) {
            return defaultValue;
        }
        return preferences.getString(key, defaultValue);
    }

    private static int getInt(String key, int defaultValue) {
        SharedPreferences preferences = getPreferences();
        if (preferences == null) {
            return defaultValue;
        }
        return preferences.getInt(key, defaultValue);
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        SharedPreferences preferences = getPreferences();
        if (preferences == null) {
            return defaultValue;
        }
        return preferences.getBoolean(key, defaultValue);
    }

    public static SharedPreferences getPreferences() {
        Context context = State.getContext();
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences(MirrorSettingsActivity.PREF_NAME, Context.MODE_PRIVATE);

    }
}
