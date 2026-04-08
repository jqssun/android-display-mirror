package io.github.jqssun.displaymirror;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.os.IBinder;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import io.github.jqssun.displaymirror.job.Job;
import io.github.jqssun.displaymirror.job.YieldException;
import io.github.jqssun.displaymirror.shizuku.IUserService;
import io.github.jqssun.displaymirror.shizuku.UserService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rikka.shizuku.Shizuku;

public class State {
    // WeakReference to avoid leaking the activity
    private static WeakReference<MirrorMainActivity> currentActivity = new WeakReference<>(null);
    public static final MutableLiveData<MirrorUiState> uiState = new MutableLiveData<>(new MirrorUiState());
    public static FloatingButtonService floatingButtonService;
    public static String serverUuid;
    private static Job currentJob;
    public static List<String> logs = java.util.Collections.synchronizedList(new ArrayList<>());
    public static DisplaylinkState displaylinkState = new DisplaylinkState();
    private static MediaProjection mediaProjection;
    public static MediaProjection mediaProjectionInUse;
    public static int lastSingleAppDisplay;
    public static String displaylinkDeviceName;
    public static VirtualDisplay mirrorVirtualDisplay;
    public static Activity isInPureBlackActivity = null;
    public static volatile IUserService userService;
    public static Set<String> discoveredMirrorClients = new HashSet<>();

    public static MirrorMainActivity getCurrentActivity() {
        if (currentActivity == null) {
            return null;
        }
        return currentActivity.get();
    }

    public static void setCurrentActivity(MirrorMainActivity activity) {
        currentActivity = new WeakReference<>(activity);
    }

    public static final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            State.log("user service connected");
            State.userService = IUserService.Stub.asInterface(binder);
            if (State.currentActivity != null && State.currentActivity.get() != null) {
                MirrorMainActivity context = State.currentActivity.get();
                context.runOnUiThread(() -> {
                    State.resumeJob();
                });
            }
            SharedPreferences preferences = Pref.getPreferences();
            if (preferences != null && preferences.getInt("AUTO_GRANT_PERMISSION", 0) != BuildConfig.VERSION_CODE) {
                preferences.edit().putInt("AUTO_GRANT_PERMISSION", BuildConfig.VERSION_CODE).apply();
                State.log("Granted media projection and overlay permissions");
                try {
                    State.userService.executeCommand("appops set io.github.jqssun.displaymirror PROJECT_MEDIA allow");
                    State.userService.executeCommand("appops set io.github.jqssun.displaymirror SYSTEM_ALERT_WINDOW allow");
                } catch (Throwable e) {
                    // ignore
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            State.log("user service disconnected");
        }
    };

    public static Shizuku.UserServiceArgs userServiceArgs = new Shizuku.UserServiceArgs(new ComponentName(BuildConfig.APPLICATION_ID, UserService.class.getName()))
            .daemon(true)
            .tag("mirror")
            .processNameSuffix("mirror")
            .debuggable(false)
            .version(BuildConfig.VERSION_CODE);

    private static final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    public static boolean isJobRunning() {
        return currentJob != null;
    }   

    public static void startNewJob(Job job) {
        if (currentJob != null) {
            if (currentActivity != null && currentActivity.get() != null) {
                State.log("Task " + currentJob.getClass().getSimpleName() + " is already running");
            }
            return;
        }
        currentJob = job;
        try {
            State.log("Starting task " + job.getClass().getSimpleName());
            currentJob.start();
            State.log("Task " + job.getClass().getSimpleName() + " completed");
            currentJob = null;
        } catch (YieldException e) {
            State.log("Task " + job.getClass().getSimpleName() + " yielded, " + e.getMessage());
        } catch (RuntimeException e) {
            State.log("Task " + job.getClass().getSimpleName() + " failed to start");
            String stackTrace = android.util.Log.getStackTraceString(e);
            State.log("Stack trace: " + stackTrace);
            currentJob = null;
        }
    }

    public static void resumeJob() {
        if (currentJob == null) {
            return;
        }
        try {
            State.log("Resuming task " + currentJob.getClass().getSimpleName());
            currentJob.start();
            State.log("Task " + currentJob.getClass().getSimpleName() + " completed");
            currentJob = null;
        } catch (YieldException e) {
            State.log("Task " + currentJob.getClass().getSimpleName() + " yielded, " + e.getMessage());
        } catch (RuntimeException e) {
            State.log("Task " + currentJob.getClass().getSimpleName() + " failed to resume");
            String stackTrace = android.util.Log.getStackTraceString(e);
            State.log("Stack trace: " + stackTrace);
            currentJob = null;
        }
    }

    public static void resumeJobLater(long delayMillis) {
        if (currentActivity.get() != null) {
            mainHandler.postDelayed(State::resumeJob, delayMillis);
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger _logVersion = new java.util.concurrent.atomic.AtomicInteger(0);
    public static final MutableLiveData<Integer> logVersion = new MutableLiveData<>(0);

    public static void log(String message) {
        logs.add(message);
        Log.i("Mirror", message);
        logVersion.postValue(_logVersion.incrementAndGet());
    }

    public static MediaProjection getMediaProjection() {
        return mediaProjection;
    }

    public static void setMediaProjection(MediaProjection newMediaProjection) {
        if (newMediaProjection == null) {
            Log.d("State", "MediaProjection used");
            mediaProjection = null;
        } else {
            Log.d("State", "MediaProjection acquired");
            mediaProjection = newMediaProjection;
            mediaProjectionInUse = newMediaProjection;
        }
    }

    public static int getDisplaylinkVirtualDisplayId() {
        if (displaylinkState.getVirtualDisplay() == null) {
            return -1;
        }
        return displaylinkState.getVirtualDisplay().getDisplay().getDisplayId();
    }

    public static void stopMirrorVirtualDisplay() {
        if (mirrorVirtualDisplay != null) {
            mirrorVirtualDisplay.release();
            mirrorVirtualDisplay = null;
        }
    }

    public static int getMirrorVirtualDisplayId() {
        if (mirrorVirtualDisplay == null) {
            return -1;
        }
        return mirrorVirtualDisplay.getDisplay().getDisplayId();
    }

    public static void unbindUserService() {
        try {
            Shizuku.unbindUserService(State.userServiceArgs, userServiceConnection, false);
            State.userService = null;
        } catch (Exception e) {
            // ignore
        }
    }

    public static void refreshMainActivity() {
        MirrorMainActivity mirrorMainActivity = currentActivity.get();
        if (mirrorMainActivity != null) {
            mirrorMainActivity.runOnUiThread(mirrorMainActivity::refresh);
        }
    }

    public static void showErrorStatus(String msg) {
        State.log(msg);
        MirrorUiState newUiState = new MirrorUiState();
        newUiState.errorStatusText = msg;
        State.uiState.setValue(newUiState);
    }

    public static Context getContext() {
        if (currentActivity != null && currentActivity.get() != null) {
            return currentActivity.get();
        }
        if (SunshineService.instance != null) {
            return SunshineService.instance;
        }
        return null;
    }

    public static void bindUserService() {
        try {
            Shizuku.peekUserService(State.userServiceArgs, State.userServiceConnection);
            Shizuku.bindUserService(State.userServiceArgs, State.userServiceConnection);
        } catch (Exception e) {
            State.log("bindUserService failed: " + e.getMessage());
        }
    }
}
