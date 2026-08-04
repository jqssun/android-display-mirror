package io.github.jqssun.displaymirror.job;

import android.app.ActivityOptions;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import io.github.jqssun.displaymirror.MainActivity;
import io.github.jqssun.displaymirror.ProjectionService;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.airplay.AirPlayState;
import io.github.jqssun.displaymirror.displaylink.DisplaylinkState;

public class MirrorDisplayMonitor {
  private static boolean registered = false;

  public static void init(DisplayManager displayManager) {
    if (registered) {
      return;
    }
    registered = true;
    displayManager.registerDisplayListener(
        new DisplayManager.DisplayListener() {
          @Override
          public void onDisplayAdded(int displayId) {
            State.log("display added, displayId: " + displayId);
            new Handler(Looper.getMainLooper())
                .postDelayed(
                    () -> {
                      if (ProjectionService.instance == null) {
                        return;
                      }
                      String targetScreen = _resolveTargetScreen(displayId);
                      if (State.getCurrentActivity() != null) {
                        State.getCurrentActivity().finish();
                      }
                      Intent intent = new Intent(ProjectionService.instance, MainActivity.class);
                      intent.setAction(MainActivity.ACTION_OPEN_SCREEN);
                      intent.putExtra(MainActivity.EXTRA_SCREEN, targetScreen);
                      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                      ActivityOptions options = ActivityOptions.makeBasic();
                      options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
                      ProjectionService.instance.startActivity(intent, options.toBundle());
                    },
                    1000);
          }

          @Override
          public void onDisplayRemoved(int displayId) {
            State.log("display removed, displayId: " + displayId);
          }

          @Override
          public void onDisplayChanged(int displayId) {}
        },
        null);
  }

  private static String _resolveTargetScreen(int displayId) {
    MainActivity currentActivity = State.getCurrentActivity();
    if (currentActivity != null) {
      String currentScreen = currentActivity.getCurrentScreen();
      if (MainActivity.SCREEN_SUNSHINE.equals(currentScreen)
          || MainActivity.SCREEN_AIRPLAY.equals(currentScreen)
          || MainActivity.SCREEN_DISPLAYLINK.equals(currentScreen)) {
        return currentScreen;
      }
    }
    if (displayId == DisplaylinkState.getVirtualDisplayId()) {
      return MainActivity.SCREEN_DISPLAYLINK;
    }
    if (displayId == AirPlayState.getVirtualDisplayId()) {
      return MainActivity.SCREEN_AIRPLAY;
    }
    return MainActivity.SCREEN_SUNSHINE;
  }
}
