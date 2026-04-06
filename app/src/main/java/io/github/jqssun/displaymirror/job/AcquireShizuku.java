package io.github.jqssun.displaymirror.job;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.github.jqssun.displaymirror.BuildConfig;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;
import io.github.jqssun.displaymirror.shizuku.UserService;
import com.topjohnwu.superuser.Shell;

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
                fixRootShizuku();
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

    public static void fixRootShizuku() {
        if (ShizukuUtils.hasPermission() && Shizuku.getUid() == 0) {
            State.log("Detected Shizuku started as root, attempting to restart Shizuku as adb");
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    boolean success = Shell.getShell().newJob()
                            .add("/data/adb/magisk/busybox killall shizuku_server")
                            .add("su 2000")
                            .add("/data/local/tmp/shizuku_starter")
                            .exec()
                            .isSuccess();
                    Log.e("State", "kill shizuku " + success);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (success) {
                            State.log("Shizuku restarted as adb, please restart the app");
                        } else {
                            State.log("Failed to restart Shizuku");
                        }
                    });
                } catch (Throwable e) {
                    // ignore
                }
            }).start();
        }
    }
}
