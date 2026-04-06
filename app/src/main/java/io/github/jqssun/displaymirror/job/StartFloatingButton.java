package io.github.jqssun.displaymirror.job;

import android.content.Context;
import android.content.Intent;

import io.github.jqssun.displaymirror.FloatingButtonService;


public class StartFloatingButton implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    private final int displayId;
    private final Context context;

    public StartFloatingButton(int displayId, Context context) {
        this.displayId = displayId;
        this.context = context;
    }

    @Override
    public void start() throws YieldException {
        acquireShizuku.start();
        if (!acquireShizuku.acquired) {
            return;
        }
        Intent serviceIntent = new Intent(context, FloatingButtonService.class);
        serviceIntent.putExtra("display_id", displayId);
        context.startService(serviceIntent);
    }
}
