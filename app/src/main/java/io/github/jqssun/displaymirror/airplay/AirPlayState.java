package io.github.jqssun.displaymirror.airplay;

import android.view.Surface;
import io.github.jqssun.displaymirror.State;

public class AirPlayState {
  private static int virtualDisplayId = -1;
  private static Surface surface;

  public static int getVirtualDisplayId() {
    return virtualDisplayId;
  }

  public static Surface getSurface() {
    return surface;
  }

  public static void setTouchTarget(int displayId, Surface newSurface) {
    boolean changed = virtualDisplayId != displayId || surface != newSurface;
    virtualDisplayId = displayId;
    surface = newSurface;
    if (changed) {
      State.refreshMainActivity();
    }
  }

  public static void clearTouchTarget() {
    setTouchTarget(-1, null);
  }

  public static void setVirtualDisplayId(int displayId) {
    if (virtualDisplayId == displayId) {
      return;
    }
    virtualDisplayId = displayId;
    State.refreshMainActivity();
  }
}
