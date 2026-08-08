package io.github.jqssun.displaymirror.job;

import android.os.Build;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.R;

// android.hardware.display.DisplayManager
// https://github.com/LineageOS/android_frameworks_base
public class DisplayFlags {
  public static final int PUBLIC =
      android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
  public static final int PRESENTATION =
      android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
  public static final int SECURE =
      android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE;
  public static final int OWN_CONTENT_ONLY =
      android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
  public static final int CAN_SHOW_WITH_INSECURE_KEYGUARD = 1 << 5;
  public static final int SUPPORTS_TOUCH = 1 << 6;
  public static final int ROTATES_WITH_CONTENT = 1 << 7;
  public static final int DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
  public static final int SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 9;
  public static final int TRUSTED = 1 << 10;
  public static final int OWN_DISPLAY_GROUP = 1 << 11;
  public static final int ALWAYS_UNLOCKED = 1 << 12;
  public static final int TOUCH_FEEDBACK_DISABLED = 1 << 13;
  public static final int OWN_FOCUS = 1 << 14;
  public static final int DEVICE_DISPLAY_GROUP = 1 << 15;
  public static final int STEAL_TOP_FOCUS_DISABLED = 1 << 16;

  public static class Item {
    public final int bit;
    public final int nameRes;
    public final int descRes;

    Item(int bit, int nameRes, int descRes) {
      this.bit = bit;
      this.nameRes = nameRes;
      this.descRes = descRes;
    }
  }

  // own-content and rotate excluded: owned by mirror-only and rotate settings
  public static final Item[] CUSTOMIZABLE = {
    new Item(PUBLIC, R.string.flag_public, R.string.flag_public_desc),
    new Item(PRESENTATION, R.string.flag_presentation, R.string.flag_presentation_desc),
    new Item(SECURE, R.string.flag_secure, R.string.flag_secure_desc),
    new Item(
        CAN_SHOW_WITH_INSECURE_KEYGUARD,
        R.string.flag_insecure_keyguard,
        R.string.flag_insecure_keyguard_desc),
    new Item(SUPPORTS_TOUCH, R.string.flag_supports_touch, R.string.flag_supports_touch_desc),
    new Item(
        DESTROY_CONTENT_ON_REMOVAL,
        R.string.flag_destroy_content,
        R.string.flag_destroy_content_desc),
    new Item(
        SHOULD_SHOW_SYSTEM_DECORATIONS,
        R.string.flag_system_decorations,
        R.string.flag_system_decorations_desc),
    new Item(TRUSTED, R.string.flag_trusted, R.string.flag_trusted_desc),
    new Item(
        OWN_DISPLAY_GROUP, R.string.flag_own_display_group, R.string.flag_own_display_group_desc),
    new Item(ALWAYS_UNLOCKED, R.string.flag_always_unlocked, R.string.flag_always_unlocked_desc),
    new Item(
        TOUCH_FEEDBACK_DISABLED,
        R.string.flag_touch_feedback_disabled,
        R.string.flag_touch_feedback_disabled_desc),
    new Item(OWN_FOCUS, R.string.flag_own_focus, R.string.flag_own_focus_desc),
    new Item(
        DEVICE_DISPLAY_GROUP,
        R.string.flag_device_display_group,
        R.string.flag_device_display_group_desc),
    new Item(
        STEAL_TOP_FOCUS_DISABLED,
        R.string.flag_steal_top_focus_disabled,
        R.string.flag_steal_top_focus_disabled_desc),
  };

  // combos the service rejects with an exception
  public static final int[][] CONFLICTS = {{PUBLIC, CAN_SHOW_WITH_INSECURE_KEYGUARD}};

  public static int auto() {
    int flags = PUBLIC | SUPPORTS_TOUCH;
    if (Build.VERSION.SDK_INT >= AndroidVersions.API_33_ANDROID_13) {
      flags |=
          TRUSTED
              | SHOULD_SHOW_SYSTEM_DECORATIONS
              | OWN_DISPLAY_GROUP
              | ALWAYS_UNLOCKED
              | TOUCH_FEEDBACK_DISABLED;
      if (Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14) {
        flags |= DEVICE_DISPLAY_GROUP;
      }
    }
    return flags;
  }

  public static int current() {
    return Pref.getCustomDisplayFlagsEnabled() ? Pref.getCustomDisplayFlags(auto()) : auto();
  }
}
