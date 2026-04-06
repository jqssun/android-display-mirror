package io.github.jqssun.displaymirror.job;

import android.content.Context;
import android.content.Intent;

import io.github.jqssun.displaymirror.TouchpadAccessibilityService;
import io.github.jqssun.displaymirror.TouchpadActivity;


public class StartTouchPad implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    private final int displayId;
    private final Context context;

    public StartTouchPad(int displayId, Context context) {
        this.displayId = displayId;
        this.context = context;
    }

    @Override
    public void start() throws YieldException {
        acquireShizuku.start();
        if (!acquireShizuku.acquired) {
            return;
        }
        Intent intent = new Intent(context, TouchpadActivity.class);
        intent.putExtra("display_id", displayId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        TouchpadAccessibilityService.startServiceByShizuku(context);
    }
}
