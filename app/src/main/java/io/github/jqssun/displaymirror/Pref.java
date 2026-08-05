package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

public class Pref {
  public static final String PREF_NAME = "mirror_settings";

  // display
  public static final String KEY_TRUSTED_DISPLAY = "trusted_display";
  public static final String KEY_MIRROR_ONLY = "mirror_only";
  public static final String KEY_ROTATE_WITH_CONTENT = "rotate_with_content";
  public static final String KEY_CROP_BLACK_BORDERS = "crop_black_borders";
  public static final String KEY_AUTO_MATCH_ASPECT_RATIO = "auto_match_aspect_ratio";
  public static final String KEY_PREVENT_AUTO_LOCK = "prevent_auto_lock";
  // runtime marker so leftover forced size gets cleared after crash
  public static final String KEY_ASPECT_FORCED = "aspect_forced";

  // audio
  public static final String KEY_USE_GLOBAL_AUDIO_CAPTURE = "use_global_audio_capture";

  // sunshine
  public static final String KEY_SUNSHINE_INPUT_TO_EXTERNAL_DISPLAY =
      "sunshine_input_to_external_display";
  public static final String KEY_SUNSHINE_SHOW_CURSOR = "sunshine_show_cursor";
  public static final String KEY_SUNSHINE_AUTO_CONNECT_CLIENT = "sunshine_auto_connect_client";
  public static final String KEY_SUNSHINE_SELECTED_CLIENT = "sunshine_selected_client";
  public static final String KEY_SUNSHINE_DEVICE_NAME = "sunshine_device_name";

  // airplay
  public static final String KEY_AIRPLAY_APPLE_RECEIVER = "airplay_apple_receiver";
  public static final String KEY_AIRPLAY1_MODE = "airplay1_mode";

  // displaylink
  public static final String KEY_DISPLAYLINK_WIDTH = "displaylink_width";
  public static final String KEY_DISPLAYLINK_HEIGHT = "displaylink_height";
  public static final String KEY_DISPLAYLINK_REFRESH_RATE = "displaylink_refresh_rate";
  public static final String KEY_DISPLAYLINK_APK_URL = "displaylink_apk_url";
  public static final String DEFAULT_DISPLAYLINK_APK_URL =
      "https://www.synaptics.com/sites/default/files/exe_files/2024-12/DisplayLink%C2%AE%20USB%20Graphics%20Software%20for%20Android%204.2.0-EXE.apk";

  public static boolean doNotAutoStartSunshine;

  public static boolean getTrustedDisplay() {
    return getBoolean(KEY_TRUSTED_DISPLAY, true);
  }

  // when on, bind a MediaProjection so the display mirrors the screen
  public static boolean getMirrorOnly() {
    return getBoolean(KEY_MIRROR_ONLY, false);
  }

  public static boolean getRotateWithContent() {
    return getBoolean(KEY_ROTATE_WITH_CONTENT, false);
  }

  public static boolean getCropBlackBorders() {
    return getBoolean(KEY_CROP_BLACK_BORDERS, false);
  }

  public static boolean getSunshineInputToExternalDisplay() {
    return getBoolean(KEY_SUNSHINE_INPUT_TO_EXTERNAL_DISPLAY, false);
  }

  public static boolean getAutoMatchAspectRatio() {
    return getBoolean(KEY_AUTO_MATCH_ASPECT_RATIO, false);
  }

  public static boolean getAspectForced() {
    return getBoolean(KEY_ASPECT_FORCED, false);
  }

  public static void setAspectForced(boolean forced) {
    SharedPreferences preferences = getPreferences();
    if (preferences != null) {
      preferences.edit().putBoolean(KEY_ASPECT_FORCED, forced).apply();
    }
  }

  public static boolean getPreventAutoLock() {
    return getBoolean(KEY_PREVENT_AUTO_LOCK, false);
  }

  public static boolean getUseGlobalAudioCapture() {
    return getBoolean(KEY_USE_GLOBAL_AUDIO_CAPTURE, false);
  }

  public static boolean getSunshineShowCursor() {
    return getBoolean(KEY_SUNSHINE_SHOW_CURSOR, false);
  }

  public static boolean getSunshineAutoConnectClient() {
    return getBoolean(KEY_SUNSHINE_AUTO_CONNECT_CLIENT, false);
  }

  public static String getSunshineSelectedClient() {
    return getString(KEY_SUNSHINE_SELECTED_CLIENT, "");
  }

  public static String getSunshineDeviceName() {
    String name = getString(KEY_SUNSHINE_DEVICE_NAME, "");
    return name.isEmpty() ? getDefaultSunshineDeviceName() : name;
  }

  public static String getDefaultSunshineDeviceName() {
    return "Mirror-" + Build.MANUFACTURER + "-" + Build.MODEL;
  }

  public static boolean getAirPlayAppleReceiver() {
    return getBoolean(KEY_AIRPLAY_APPLE_RECEIVER, true);
  }

  public static boolean getAirPlay1Mode() {
    return getBoolean(KEY_AIRPLAY1_MODE, false);
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

  public static String getDisplaylinkApkUrl() {
    return getString(KEY_DISPLAYLINK_APK_URL, DEFAULT_DISPLAYLINK_APK_URL);
  }

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
