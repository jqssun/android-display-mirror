package io.github.jqssun.displaymirror.job;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.DisplayHidden;
import android.view.IWindowManager;

import io.github.jqssun.displaymirror.BridgeActivity;
import io.github.jqssun.displaymirror.MirrorActivity;
import io.github.jqssun.displaymirror.MirrorMainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import dev.rikka.tools.refine.Refine;

public class ProjectViaMirror implements Job {
    private static int TYPE_WIFI = 3;
    private Thread waitThread;
    private final Display mirrorDisplay;
    private boolean mediaProjectionRequested;

    public ProjectViaMirror(Display mirrorDisplay) {
        this.mirrorDisplay = mirrorDisplay;
    }

    @Override
    public void start() throws YieldException {
        if (waitThread != null) {
            waitThread.interrupt();
            waitThread = null;
        }
        Context context = State.getContext();
        if (context == null) {
            return;
        }
        boolean singleAppMode = Pref.getSingleAppMode();
        boolean useTouchscreen = Pref.getUseTouchscreen();
        if (singleAppMode && (!ShizukuUtils.hasPermission() || !useTouchscreen)) {
            String selectedAppPackage = Pref.getSelectedAppPackage();
            ServiceUtils.launchPackage(context, selectedAppPackage, mirrorDisplay.getDisplayId());
            State.refreshMainActivity();
            CreateVirtualDisplay.powerOffScreen();
            int targetDisplayId = mirrorDisplay.getDisplayId();
            if (ShizukuUtils.hasPermission()) {
                int singleAppDpi = Pref.getSingleAppDpi();
                IWindowManager wm = ServiceUtils.getWindowManager();
                wm.setForcedDisplayDensityForUser(targetDisplayId, singleAppDpi, 0);
                InputRouting.moveImeToExternal(targetDisplayId);
            }
            DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int i) {

                }

                @Override
                public void onDisplayRemoved(int i) {
                    if (i == targetDisplayId) {
                        CreateVirtualDisplay.powerOnScreen();
                        ExitAll.execute(State.getContext(), false);
                    }
                }

                @Override
                public void onDisplayChanged(int i) {

                }
            }, null);
            return;
        }
        if (_requestMediaProjectionPermission(State.getCurrentActivity(), singleAppMode)) {
            if (singleAppMode) {
                Point initialSize = new Point();
                ServiceUtils.getWindowManager().getInitialDisplaySize(mirrorDisplay.getDisplayId(), initialSize);
                Intent intent = new Intent(context, BridgeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("virtualDisplayArgs", new VirtualDisplayArgs(
                        "Mirror",
                        initialSize.x,
                        initialSize.y,
                        (int) mirrorDisplay.getRefreshRate(),
                        Pref.getSingleAppDpi(),
                        Pref.getAutoRotate()));
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(mirrorDisplay.getDisplayId());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                    if (!activityManager.isActivityStartAllowedOnDisplay(context, mirrorDisplay.getDisplayId(), intent)) {
                        Log.d("ProjectViaMirror", "This display does not allow launching activities, displayId: " + mirrorDisplay.getDisplayId());
                        return;
                    }
                }
                context.startActivity(intent, options.toBundle());
            } else {
                Intent intent = new Intent(context, MirrorActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(mirrorDisplay.getDisplayId());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                    if (!activityManager.isActivityStartAllowedOnDisplay(context, mirrorDisplay.getDisplayId(), intent)) {
                        Log.d("ProjectViaMirror", "This display does not allow launching activities, displayId: " + mirrorDisplay.getDisplayId());
                        return;
                    }
                }
                context.startActivity(intent, options.toBundle());
            }
        }
    }

    private boolean _requestMediaProjectionPermission(Context context, boolean singleAppMode) throws YieldException {
        DisplayHidden displayHidden = Refine.unsafeCast(mirrorDisplay);
        if (displayHidden.getType() == TYPE_WIFI) {
            if (ShizukuUtils.hasPermission() && singleAppMode) {
                return true;
            }   
            return false;
        }
        if (State.mirrorVirtualDisplay != null) {
            return true;
        }
        if (State.getMediaProjection() != null) {
            State.log("MediaProjection already exists, skipping duplicate request");
            return true;
        }
        if (mediaProjectionRequested) {
            State.log("Skipping task, screen projection permission not granted");
            return false;
        }
        mediaProjectionRequested = true;
        MirrorMainActivity mirrorMainActivity = State.getCurrentActivity();
        if (mirrorMainActivity == null) {
            return false;
        }
        mirrorMainActivity.startMediaProjectionService();
        throw new YieldException("Waiting for projection permission");
    }

}
