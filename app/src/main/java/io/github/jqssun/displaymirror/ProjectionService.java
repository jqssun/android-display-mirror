package io.github.jqssun.displaymirror;

import static android.app.Activity.RESULT_OK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import io.github.jqssun.displaymirror.job.MirrorDisplayMonitor;
import io.github.jqssun.displaymirror.shizuku.PermissionManager;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

public class ProjectionService extends Service {
  public static ProjectionService instance;
  private static final String CHANNEL_ID = "ProjectionServiceChannel";
  private static final int NOTIFICATION_ID = 2;
  private static final String TAG = "ProjectionService";

  private int currentTimeout;

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
    createNotificationChannel();
    startForeground(NOTIFICATION_ID, createNotification());
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    instance = null;
    releaseWakeLock();
    State.unbindUserService();
  }

  public void releaseWakeLock() {
    if (currentTimeout > 0) {
      Settings.System.putInt(
          this.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, currentTimeout);
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && intent.hasExtra("data")) {
      MediaProjectionManager mpm =
          (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
      Intent data = intent.getParcelableExtra("data");
      if (mpm == null || data == null) {
        State.log("failed to get media projection service or data");
        return START_NOT_STICKY;
      }
      State.setMediaProjection(mpm.getMediaProjection(RESULT_OK, data));
      if (State.getMediaProjection() == null) {
        State.log("failed to get media projection");
        return START_NOT_STICKY;
      }
      State.getMediaProjection()
          .registerCallback(
              new MediaProjection.Callback() {
                @Override
                public void onStop() {
                  super.onStop();
                  State.log("MediaProjection onStop callback");
                }
              },
              null);
      State.resumeJob();
      State.fireProjectionReady();
    } else {
      State.log("ProjectionService received invalid authorization data");
      State.resumeJob();
    }
    if (Pref.getPreventAutoLock()) {
      preventAutoLock();
    }

    // pre-cache Shizuku service binders on main thread so they work from render threads
    if (ShizukuUtils.hasPermission()) {
      ServiceUtils.ensureInitialized();
    }

    DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
    MirrorDisplayMonitor.init(displayManager);
    State.refreshMainActivity();

    Handler handler = new Handler();
    handler.postDelayed(
        () -> {
          if (ShizukuUtils.hasPermission() && State.userService == null) {
            State.log("try start Shizuku user service");
            State.bindUserService();
            handler.postDelayed(
                () -> {
                  if (ShizukuUtils.hasPermission() && State.userService == null) {
                    State.log(
                        "Shizuku user service failed to start, please revoke and re-grant Shizuku authorization. try start user service again");
                    State.unbindUserService();
                    State.bindUserService();
                  }
                },
                15 * 1000);
          }
        },
        2000);
    return START_NOT_STICKY;
  }

  private void preventAutoLock() {
    if (!ShizukuUtils.hasPermission()) {
      return;
    }
    if (PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
      currentTimeout =
          Settings.System.getInt(this.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 0);
      Log.i(TAG, "Current screen timeout: " + currentTimeout + "ms");
      if (currentTimeout >= 4 * 60 * 60 * 1000) {
        currentTimeout = 15 * 1000;
      }
      Settings.System.putInt(
          this.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 4 * 60 * 60 * 1000);
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void createNotificationChannel() {
    NotificationChannel serviceChannel =
        new NotificationChannel(
            CHANNEL_ID, "Screen Projection", NotificationManager.IMPORTANCE_LOW);
    NotificationManager manager = getSystemService(NotificationManager.class);
    manager.createNotificationChannel(serviceChannel);
  }

  private Notification createNotification() {
    return new NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.mirror_app_name))
        .setContentText(getString(R.string.projection_service_running))
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .build();
  }
}
