package io.github.jqssun.displaymirror.job;

import android.widget.Toast;

import io.github.jqssun.displaymirror.R;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import rikka.shizuku.Shizuku;

public class AcquireShizuku implements Job {
    public static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;
    private boolean hasRequestedPermission;
    public boolean acquired = false;

    @Override
    public void start() throws YieldException {
        if (!ShizukuUtils.hasShizukuStarted()) {
            return;
        }
        if (ShizukuUtils.hasPermission()) {
            State.log("Shizuku permission already granted");
            acquired = true;
            if (hasRequestedPermission) {
                _notifyIfUidDropped();
                State.bindUserService();
            }
        } else {
            if (hasRequestedPermission) {
                State.log("Failed to acquire Shizuku permission");
                return;
            }
            hasRequestedPermission = true;
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
            throw new YieldException("Waiting for Shizuku permission");
        }
    }

    public static void notifyIfUidDropped() {
        _notifyIfUidDropped();
    }

    private static void _notifyIfUidDropped() {
        if (!ShizukuUtils.hasPermission() || Shizuku.getUid() != 0) return;
        android.content.Context context = State.getContext();
        int shellUid = android.os.Process.SHELL_UID;
        String msg = context != null
                ? context.getString(R.string.shizuku_uid_dropped, shellUid)
                : "Shizuku root dropped to UID " + shellUid;
        State.log(msg);
        if (context != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            );
        }
    }
}
