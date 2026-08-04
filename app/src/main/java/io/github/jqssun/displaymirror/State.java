package io.github.jqssun.displaymirror;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rikka.shizuku.Shizuku;

public class State {
  // job slot for non-path jobs like FetchLogAndShare; paths declare their own slot keys
  public static final String MODE_UTILITY = "utility";

  // weak reference to avoid leaking the activity
  private static WeakReference<MainActivity> currentActivity = new WeakReference<>(null);
  public static final MutableLiveData<MirrorUiState> uiState =
      new MutableLiveData<>(new MirrorUiState());
  private static final Map<String, Job> jobs = new HashMap<>();
  public static List<String> logs = java.util.Collections.synchronizedList(new ArrayList<>());
  private static MediaProjection mediaProjection;
  public static MediaProjection mediaProjectionInUse;
  public static volatile IUserService userService;

  public static MainActivity getCurrentActivity() {
    if (currentActivity == null) {
      return null;
    }
    return currentActivity.get();
  }

  public static void setCurrentActivity(MainActivity activity) {
    currentActivity = new WeakReference<>(activity);
  }

  public static final ServiceConnection userServiceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
          State.log("user service connected");
          State.userService = IUserService.Stub.asInterface(binder);
          if (State.currentActivity != null && State.currentActivity.get() != null) {
            MainActivity context = State.currentActivity.get();
            context.runOnUiThread(
                () -> {
                  State.resumeJob();
                });
          }
          SharedPreferences preferences = Pref.getPreferences();
          if (preferences != null
              && preferences.getInt("AUTO_GRANT_PERMISSION", 0) != BuildConfig.VERSION_CODE) {
            preferences.edit().putInt("AUTO_GRANT_PERMISSION", BuildConfig.VERSION_CODE).apply();
            State.log("granted media projection and overlay permissions");
            try {
              State.userService.executeCommand(
                  "appops set io.github.jqssun.displaymirror PROJECT_MEDIA allow");
              State.userService.executeCommand(
                  "appops set io.github.jqssun.displaymirror SYSTEM_ALERT_WINDOW allow");
            } catch (Throwable e) {
              // ignore
            }
          }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
          State.log("user service disconnected");
          State.userService = null;
        }

        @Override
        public void onBindingDied(ComponentName componentName) {
          State.log("user service binding died");
          State.userService = null;
        }
      };

  public static Shizuku.UserServiceArgs userServiceArgs =
      new Shizuku.UserServiceArgs(
              new ComponentName(BuildConfig.APPLICATION_ID, UserService.class.getName()))
          .daemon(true)
          .tag("mirror")
          .processNameSuffix("mirror")
          .debuggable(false)
          .version(BuildConfig.VERSION_CODE);

  private static final android.os.Handler mainHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());

  public static boolean isJobRunning(String mode) {
    return jobs.containsKey(mode);
  }

  /** start a job in a specific mode slot. Different modes can run concurrently. */
  public static void startNewJob(String mode, Job job) {
    if (jobs.containsKey(mode)) {
      State.log(
          "task " + jobs.get(mode).getClass().getSimpleName() + " is already running for " + mode);
      return;
    }
    jobs.put(mode, job);
    _run(mode, job, "starting");
  }

  /** resume all yielded jobs (e.g. after permission grant). */
  public static void resumeJob() {
    for (Map.Entry<String, Job> entry : new HashMap<>(jobs).entrySet()) {
      _run(entry.getKey(), entry.getValue(), "resuming");
    }
  }

  /** resume the job in a specific mode slot. */
  public static void resumeJob(String mode) {
    Job job = jobs.get(mode);
    if (job != null) {
      _run(mode, job, "resuming");
    }
  }

  public static void resumeJobLater(String mode, long delayMillis) {
    if (currentActivity.get() != null) {
      mainHandler.postDelayed(() -> resumeJob(mode), delayMillis);
    }
  }

  private static void _run(String mode, Job job, String verb) {
    String name = job.getClass().getSimpleName();
    try {
      State.log(verb + " task " + name + " [" + mode + "]");
      job.start();
      State.log("task " + name + " completed [" + mode + "]");
      jobs.remove(mode);
    } catch (YieldException e) {
      State.log("task " + name + " yielded [" + mode + "], " + e.getMessage());
    } catch (RuntimeException e) {
      State.log("task " + name + " failed [" + mode + "]");
      State.log("stacktrace: " + Log.getStackTraceString(e));
      jobs.remove(mode);
    }
  }

  private static final java.util.concurrent.atomic.AtomicInteger _logVersion =
      new java.util.concurrent.atomic.AtomicInteger(0);
  public static final MutableLiveData<Integer> logVersion = new MutableLiveData<>(0);

  public static void log(String message) {
    logs.add(message);
    Log.i("Mirror", message);
    logVersion.postValue(_logVersion.incrementAndGet());
  }

  private static Runnable projectionReadyCallback;

  // one-shot, fired when projection is set
  public static void setProjectionReadyCallback(Runnable callback) {
    projectionReadyCallback = callback;
  }

  public static void fireProjectionReady() {
    Runnable callback = projectionReadyCallback;
    projectionReadyCallback = null;
    if (callback != null) {
      callback.run();
    }
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

  public static void unbindUserService() {
    try {
      Shizuku.unbindUserService(State.userServiceArgs, userServiceConnection, false);
      State.userService = null;
    } catch (Exception e) {
      // ignore
    }
  }

  public static void refreshMainActivity() {
    MainActivity mirrorMainActivity = currentActivity.get();
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
    if (ProjectionService.instance != null) {
      return ProjectionService.instance;
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
