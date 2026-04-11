package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.SharedPreferences;

public class Pref {
    public static final String PREF_NAME = "mirror_settings";

    // Display
    public static final String KEY_TRUSTED_DISPLAY = "trusted_display";
    public static final String KEY_AUTO_ROTATE = "auto_rotate";
    public static final String KEY_AUTO_SCALE = "auto_scale";
    public static final String KEY_DISABLE_USB_AUDIO = "disable_usb_audio";

    // Moonlight
    public static final String KEY_AUTO_MATCH_ASPECT_RATIO = "auto_match_aspect_ratio";
    public static final String KEY_PREVENT_AUTO_LOCK = "prevent_auto_lock";
    public static final String KEY_SHOW_MOONLIGHT_CURSOR = "show_moonlight_cursor";
    public static final String KEY_AUTO_CONNECT_CLIENT = "auto_connect_client";
    public static final String KEY_SELECTED_CLIENT = "selected_client";
    public static final String KEY_DISABLE_REMOTE_SUBMIX = "disable_remote_submix";

    // DisplayLink
    public static final String KEY_DISPLAYLINK_WIDTH = "displaylink_width";
    public static final String KEY_DISPLAYLINK_HEIGHT = "displaylink_height";
    public static final String KEY_DISPLAYLINK_REFRESH_RATE = "displaylink_refresh_rate";
    public static final String KEY_DISPLAYLINK_APK_URL = "displaylink_apk_url";
    public static final String DEFAULT_DISPLAYLINK_APK_URL = "https://www.synaptics.com/sites/default/files/exe_files/2024-12/DisplayLink%C2%AE%20USB%20Graphics%20Software%20for%20Android%204.2.0-EXE.apk";

    public static boolean doNotAutoStartMoonlight;

    public static boolean getTrustedDisplay() { return getBoolean(KEY_TRUSTED_DISPLAY, true); }
    public static boolean getAutoRotate() { return getBoolean(KEY_AUTO_ROTATE, false); }
    public static boolean getAutoScale() { return getBoolean(KEY_AUTO_SCALE, true); }
    public static boolean getDisableUsbAudio() { return getBoolean(KEY_DISABLE_USB_AUDIO, false); }

    public static boolean getAutoMatchAspectRatio() { return getBoolean(KEY_AUTO_MATCH_ASPECT_RATIO, false); }
    public static boolean getPreventAutoLock() { return getBoolean(KEY_PREVENT_AUTO_LOCK, false); }
    public static boolean getShowMoonlightCursor() { return getBoolean(KEY_SHOW_MOONLIGHT_CURSOR, false); }
    public static boolean getAutoConnectClient() { return getBoolean(KEY_AUTO_CONNECT_CLIENT, false); }
    public static String getSelectedClient() { return getString(KEY_SELECTED_CLIENT, ""); }
    public static boolean getDisableRemoteSubmix() { return getBoolean(KEY_DISABLE_REMOTE_SUBMIX, false); }

    public static int getDisplaylinkWidth() { return getInt(KEY_DISPLAYLINK_WIDTH, 1920); }
    public static int getDisplaylinkHeight() { return getInt(KEY_DISPLAYLINK_HEIGHT, 1080); }
    public static int getDisplaylinkRefreshRate() { return getInt(KEY_DISPLAYLINK_REFRESH_RATE, 60); }
    public static String getDisplaylinkApkUrl() { return getString(KEY_DISPLAYLINK_APK_URL, DEFAULT_DISPLAYLINK_APK_URL); }

    private static String getString(String key, String defaultValue) {
        SharedPreferences preferences = getPreferences();
        return preferences == null ? defaultValue : preferences.getString(key, defaultValue);
    }

    private static int getInt(String key, int defaultValue) {
        SharedPreferences preferences = getPreferences();
        return preferences == null ? defaultValue : preferences.getInt(key, defaultValue);
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        SharedPreferences preferences = getPreferences();
        return preferences == null ? defaultValue : preferences.getBoolean(key, defaultValue);
    }

    public static SharedPreferences getPreferences() {
        Context context = State.getContext();
        return context == null ? null : context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}
