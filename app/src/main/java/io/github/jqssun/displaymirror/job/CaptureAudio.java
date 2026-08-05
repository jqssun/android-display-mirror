package io.github.jqssun.displaymirror.job;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.RemoteException;
import androidx.core.app.ActivityCompat;
import io.github.jqssun.displaymirror.MainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

/*
audio capture shared by every mirror path, from one of two sources:
- REMOTE_SUBMIX takes whole output mix; needs CAPTURE_AUDIO_OUTPUT so it records in Shizuku process, and silences local speaker
- playback capture is the unprivileged fallback: needs MediaProjection and RECORD_AUDIO, leaves local playback alone, does not work on ALLOW_CAPTURE_BY_NONE apps
*/

/*
each sink picks its own PCM format: 
- Moonlight 48kHz float for Opus
- AirPlay 44.1kHz S16LE for RAOP
*/
public final class CaptureAudio {

  private CaptureAudio() {
    // not instantiable
  }

  private static final int CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
  private static final long SUBMIX_BIND_TIMEOUT_MS = 5000;

  private static boolean permissionRequested;

  // interleaved stereo PCM in whatever encoding the stream was opened with
  public interface PcmStream {
    int read(byte[] pcm);

    void close();
  }

  public static boolean useRemoteSubmix() {
    return Pref.getUseGlobalAudioCapture()
        && State.userService != null
        && Build.VERSION.SDK_INT >= AndroidVersions.API_31_ANDROID_12;
  }

  public static boolean hasPermission(Context context) {
    return ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED;
  }

  public static boolean requestPermission() {
    Context context = State.getContext();
    if (context != null && hasPermission(context)) {
      return false;
    }
    if (permissionRequested) {
      return false;
    }
    MainActivity activity = State.getCurrentActivity();
    if (activity == null) {
      return false;
    }
    permissionRequested = true;
    activity.requestRecordAudioPermission();
    return true;
  }

  /*
  S16LE stream from whichever source is enabled, null when neither is
  RECORD_AUDIO only matters for playback capture; submix records in Shizuku process
  not used by Moonlight, its native side reads float off the recorder object
  */
  public static PcmStream openPcm16(MediaProjection projection, int sampleRate) {
    if (Pref.getUseGlobalAudioCapture()) {
      // projection service kicks the bind async, so first capture races it
      if (ShizukuUtils.hasPermission()) {
        State.awaitUserService(SUBMIX_BIND_TIMEOUT_MS);
      }
      if (!useRemoteSubmix()) {
        State.log("audio capture: REMOTE_SUBMIX unavailable (no Shizuku service)");
      } else if (startRemoteSubmix(sampleRate, AudioFormat.ENCODING_PCM_16BIT)) {
        State.log("audio capture: REMOTE_SUBMIX via Shizuku at " + sampleRate + "Hz");
        return _submixPcm16();
      } else {
        State.log("audio capture: REMOTE_SUBMIX start failed, falling back to playback capture");
      }
    }
    Context context = State.getContext();
    if (context == null) {
      return null;
    }
    if (!hasPermission(context)) {
      State.log("audio capture: recording permission not granted");
      return null;
    }
    AudioRecord record =
        startPlaybackCapture(projection, sampleRate, AudioFormat.ENCODING_PCM_16BIT);
    if (record == null) {
      return null;
    }
    State.log("audio capture: playback capture");
    return new PcmStream() {
      @Override
      public int read(byte[] pcm) {
        return record.read(pcm, 0, pcm.length);
      }

      @Override
      public void close() {
        try {
          record.stop();
        } catch (Exception ignored) {
        }
        record.release();
      }
    };
  }

  // recording AudioRecord over projection playback, null when unavailable
  public static AudioRecord startPlaybackCapture(
      MediaProjection projection, int sampleRate, int encoding) {
    if (Build.VERSION.SDK_INT < AndroidVersions.API_29_ANDROID_10) {
      State.log("playback capture needs Android 10 or newer");
      return null;
    }
    if (projection == null) {
      State.log("playback capture needs a media projection");
      return null;
    }
    try {
      AudioPlaybackCaptureConfiguration config =
          new AudioPlaybackCaptureConfiguration.Builder(projection)
              .excludeUsage(AudioAttributes.USAGE_ALARM)
              .build();
      AudioRecord record =
          new AudioRecord.Builder()
              .setAudioPlaybackCaptureConfig(config)
              .setAudioFormat(
                  new AudioFormat.Builder()
                      .setEncoding(encoding)
                      .setSampleRate(sampleRate)
                      .setChannelMask(CHANNELS)
                      .build())
              // does not affect latency
              .setBufferSizeInBytes(
                  2 * AudioRecord.getMinBufferSize(sampleRate, CHANNELS, encoding))
              .build();
      record.startRecording();
      return record;
    } catch (Exception e) {
      State.log("playback capture failed: " + e.getMessage());
      return null;
    }
  }

  private static PcmStream _submixPcm16() {
    return new PcmStream() {
      @Override
      public int read(byte[] pcm) {
        return readRemoteSubmixPcm16(pcm);
      }

      @Override
      public void close() {
        stopRemoteSubmix();
      }
    };
  }

  // one submix recorder per app: concurrent paths must share format, last out stops it
  private static int submixUsers;
  private static int submixSampleRate;
  private static int submixEncoding;

  public static synchronized boolean startRemoteSubmix(int sampleRate, int encoding) {
    if (State.userService == null) {
      return false;
    }
    if (submixUsers > 0) {
      if (sampleRate != submixSampleRate || encoding != submixEncoding) {
        State.log("REMOTE_SUBMIX already capturing in another format");
        return false;
      }
      submixUsers++;
      return true;
    }
    try {
      if (!State.userService.startRecordingAudio(sampleRate, encoding)) {
        return false;
      }
    } catch (RemoteException e) {
      State.log("REMOTE_SUBMIX capture failed: " + e.getMessage());
      return false;
    }
    submixSampleRate = sampleRate;
    submixEncoding = encoding;
    submixUsers = 1;
    return true;
  }

  // negative on failure like AudioRecord.read, so callers can tell it from silence
  public static int readRemoteSubmixPcm16(byte[] pcm) {
    if (State.userService == null) {
      return AudioRecord.ERROR_DEAD_OBJECT;
    }
    try {
      return State.userService.readAudioPcm16(pcm);
    } catch (RemoteException e) {
      State.log("REMOTE_SUBMIX read failed: " + e.getMessage());
      return AudioRecord.ERROR_DEAD_OBJECT;
    }
  }

  public static synchronized void stopRemoteSubmix() {
    if (submixUsers == 0 || --submixUsers > 0 || State.userService == null) {
      return;
    }
    try {
      State.userService.stopRecordingAudio();
    } catch (RemoteException e) {
      // ignore
    }
  }
}
