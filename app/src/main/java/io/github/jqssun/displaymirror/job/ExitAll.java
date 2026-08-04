package io.github.jqssun.displaymirror.job;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import io.github.jqssun.displaymirror.BuildConfig;
import io.github.jqssun.displaymirror.CastPlaceholderActivity;
import io.github.jqssun.displaymirror.ProjectionService;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.airplay.AirPlayService;
import io.github.jqssun.displaymirror.airplay.AirPlayState;
import io.github.jqssun.displaymirror.displaylink.DisplaylinkState;
import io.github.jqssun.displaymirror.sunshine.SunshineAudio;
import io.github.jqssun.displaymirror.sunshine.SunshineServer;
import io.github.jqssun.displaymirror.sunshine.SunshineState;

public class ExitAll {
  public static void execute(Context context, boolean restart) {
    CastPlaceholderActivity.dismiss();
    if (ProjectionService.instance != null) {
      ProjectionService.instance.releaseWakeLock();
    }
    boolean wasSunshineStarted = SunshineServer.exitServer();
    AirPlayService.getInstance().disconnect();
    CreateVirtualDisplay.restoreAspectRatio();
    SunshineAudio.restoreVolume(context);
    State.unbindUserService();
    if (State.mediaProjectionInUse != null) {
      State.mediaProjectionInUse.stop();
      State.mediaProjectionInUse = null;
    }
    State.setMediaProjection(null);
    if (restart) {
      PackageManager packageManager = context.getPackageManager();
      Intent intent = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
      if (intent == null) return;
      ComponentName componentName = intent.getComponent();
      Intent mainIntent = Intent.makeRestartActivityTask(componentName);
      mainIntent.setPackage(context.getPackageName());
      mainIntent.putExtra("DoNotAutoStartSunshine", true);
      context.startActivity(mainIntent);
    }

    SunshineState.stopVirtualDisplay();
    AirPlayState.setVirtualDisplayId(-1);
    DisplaylinkState.instance.destroy();

    if (context != null) {
      context.stopService(new Intent(context, ProjectionService.class));
    }
    System.exit(0);
    try {
      android.os.Process.killProcess(android.os.Process.myPid());
    } catch (Throwable e) {
      // ignore
    }
  }
}
