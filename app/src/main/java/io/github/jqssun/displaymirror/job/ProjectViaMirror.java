package io.github.jqssun.displaymirror.job;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.Display;

import io.github.jqssun.displaymirror.MirrorActivity;
import io.github.jqssun.displaymirror.MirrorMainActivity;
import io.github.jqssun.displaymirror.State;

public class ProjectViaMirror implements Job {
    private final Display mirrorDisplay;
    private boolean mediaProjectionRequested;

    public ProjectViaMirror(Display mirrorDisplay) {
        this.mirrorDisplay = mirrorDisplay;
    }

    @Override
    public void start() throws YieldException {
        Context context = State.getContext();
        if (context == null) {
            return;
        }
        if (_requestMediaProjectionPermission()) {
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
            State.setLastSingleAppDisplay(mirrorDisplay.getDisplayId());
        }
    }

    private boolean _requestMediaProjectionPermission() throws YieldException {
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
