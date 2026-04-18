package io.github.jqssun.displaymirror.job;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import io.github.jqssun.displaymirror.BuildConfig;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.SunshineService;

public class ExitAll {
    public static void execute(Context context, boolean restart) {
        if (SunshineService.instance != null) {
            SunshineService.instance.releaseWakeLock();
        }
        boolean wasSunshineStarted = SunshineServer.exitServer();
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
            mainIntent.putExtra("DoNotAutoStartMoonlight", true);
            context.startActivity(mainIntent);
        }

        State.stopMirrorVirtualDisplay();
        State.clearLastSingleAppDisplay();
        State.setAirPlayVirtualDisplayId(-1);
        State.displaylinkState.destroy();

        if (context != null) {
            context.stopService(new Intent(context, SunshineService.class));
        }
        System.exit(0);
        try {
            android.os.Process.killProcess(android.os.Process.myPid());
        } catch(Throwable e) {
            // ignore
        }
    }
}
