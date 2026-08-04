package io.github.jqssun.displaymirror.sunshine;

import android.hardware.display.VirtualDisplay;
import android.view.Display;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import java.util.HashSet;
import java.util.Set;

public class SunshineState {
  // job slot key
  public static final String MODE = "sunshine";

  public static VirtualDisplay virtualDisplay;
  public static String serverUuid;
  public static Set<String> discoveredClients = new HashSet<>();

  public static void setVirtualDisplay(VirtualDisplay newVirtualDisplay) {
    if (virtualDisplay == newVirtualDisplay) {
      return;
    }
    virtualDisplay = newVirtualDisplay;
    State.refreshMainActivity();
  }

  public static void stopVirtualDisplay() {
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
