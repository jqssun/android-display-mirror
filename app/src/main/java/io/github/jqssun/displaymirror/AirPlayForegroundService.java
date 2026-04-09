package io.github.jqssun.displaymirror;

import static android.app.Activity.RESULT_OK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import io.github.jqssun.displaymirror.job.AirPlayService;

public class AirPlayForegroundService extends Service {
    private static final String CHANNEL_ID = "AirPlayServiceChannel";
    private static final int NOTIFICATION_ID = 3;

    @Override
    public void onCreate() {
        super.onCreate();
        _createChannel();
        startForeground(NOTIFICATION_ID, _buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("data")) {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent data = intent.getParcelableExtra("data");
            if (mpm != null && data != null) {
                MediaProjection proj = mpm.getMediaProjection(RESULT_OK, data);
                if (proj != null) {
                    AirPlayService.getInstance().onProjectionReady(proj);
                }
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AirPlayService.getInstance().disconnect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void _createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "AirPlay", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification _buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AirPlay")
                .setContentText("Streaming to AirPlay receiver")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .build();
    }
}
