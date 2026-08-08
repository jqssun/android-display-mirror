package io.github.jqssun.displaymirror.sunshine;

import android.hardware.display.VirtualDisplay;
import android.view.Display;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.job.StreamRenderer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SunshineState {
  // job slot key prefix, one slot per session
  public static final String MODE = "sunshine";

  // mirror: shared display; extend: last created
  public static VirtualDisplay virtualDisplay;
  public static StreamRenderer pipeline;
  public static String serverUuid;
  public static Set<String> discoveredClients = new HashSet<>();

  // main-thread confined
  private static final Set<Long> sessions = new HashSet<>();
  private static final Map<Long, VirtualDisplay> sessionDisplays = new HashMap<>();

  public static void addSession(long session) {
    sessions.add(session);
  }

  public static void addSessionDisplay(long session, VirtualDisplay display) {
    sessions.add(session);
    if (display != null) {
      sessionDisplays.put(session, display);
    }
    setVirtualDisplay(display);
  }

  /** drops one session's resources; true when it was the last */
  public static boolean releaseSession(long session) {
    sessions.remove(session);
    if (pipeline != null) {
      pipeline.removeOutput(session);
    }
    VirtualDisplay display = sessionDisplays.remove(session);
    if (display != null) {
      if (display == virtualDisplay) {
        VirtualDisplay next =
            sessionDisplays.isEmpty() ? null : sessionDisplays.values().iterator().next();
        setVirtualDisplay(next);
      }
      display.release();
    }
    return sessions.isEmpty();
  }

  public static void setVirtualDisplay(VirtualDisplay newVirtualDisplay) {
    if (virtualDisplay == newVirtualDisplay) {
      return;
    }
    virtualDisplay = newVirtualDisplay;
    State.refreshMainActivity();
  }

  public static void stopVirtualDisplay() {
    if (pipeline != null) {
      pipeline.stop();
      pipeline = null;
    }
    sessions.clear();
    for (VirtualDisplay display : sessionDisplays.values()) {
      if (display != virtualDisplay) {
        display.release();
      }
    }
    sessionDisplays.clear();
    if (virtualDisplay != null) {
      virtualDisplay.release();
      virtualDisplay = null;
      State.refreshMainActivity();
    }
  }

  public static int getVirtualDisplayId() {
    if (virtualDisplay == null) {
      return -1;
    }
    return virtualDisplay.getDisplay().getDisplayId();
  }

  public static boolean inputToExternalDisplay() {
    return virtualDisplay != null && Pref.getSunshineInputToExternalDisplay();
  }

  public static int getInputDisplayId() {
    return inputToExternalDisplay()
        ? virtualDisplay.getDisplay().getDisplayId()
        : Display.DEFAULT_DISPLAY;
  }
}
