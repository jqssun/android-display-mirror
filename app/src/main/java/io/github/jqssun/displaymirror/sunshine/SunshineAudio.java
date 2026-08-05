package io.github.jqssun.displaymirror.sunshine;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Looper;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.job.CaptureAudio;
import io.github.jqssun.displaymirror.job.YieldException;

public class SunshineAudio {
  // Opus config, shared by both capture sources
  private static final int SAMPLE_RATE = 48000;
  private static final int ENCODING = AudioFormat.ENCODING_PCM_FLOAT;

  private static boolean isMuted = false;
  // submix is refcounted across paths, only release refs we took
  private static boolean submixHeld;
  private static AudioManager.OnAudioFocusChangeListener volumeChangeListener;

  public static boolean sendAudio(Context context, int packetDuration) throws YieldException {
    if (CaptureAudio.useRemoteSubmix()) {
      if (CaptureAudio.startRemoteSubmix(SAMPLE_RATE, ENCODING)) {
        submixHeld = true;
        // native reads float off the proxy
        SunshineServer.startAudioRecording(
            new AudioRecordProxy(), _framesPerPacket(packetDuration));
        return false;
      }
      State.log("failed to start audio recording via Shizuku, falling back to normal audio");
    }
    if (_sendAudioUseNormalPermission(context, packetDuration)) {
      return true;
    }
    // check audio settings permission
    if (context.checkSelfPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      State.log("no audio control permission, cannot mute");
    }
    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
    if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
      isMuted = true;
      State.log("muting phone audio at client's request");
      _registerVolumeChangeListener(context, audioManager);
    } else {
      State.log("failed to set mute");
    }
    return false;
  }

  // register volume change listener method
  private static void _registerVolumeChangeListener(Context context, AudioManager audioManager) {

    // create audio focus change listener
    volumeChangeListener =
        focusChange -> {
          // if still projecting and should stay muted, check and re-mute
          if (SunshineState.virtualDisplay != null && isMuted) {
            _checkAndRestoreMute();
          }
        };

    // request audio focus to receive audio change events
    audioManager.requestAudioFocus(
        volumeChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

    // create content observer to listen for volume changes
    context
        .getContentResolver()
        .registerContentObserver(
            android.provider.Settings.System.CONTENT_URI,
            true,
            new android.database.ContentObserver(new Handler(Looper.getMainLooper())) {
              @Override
              public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                // if still projecting and should stay muted, check and re-mute
                if (SunshineState.virtualDisplay != null && isMuted) {
                  _checkAndRestoreMute();
                }
              }
            });
  }

  // check and restore mute state
  private static void _checkAndRestoreMute() {
    Context context = State.getContext();
    if (context == null) {
      return;
    }
    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (!audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
      State.log("volume change detected, reapplying mute");
      audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
    }
  }

  private static boolean _sendAudioUseNormalPermission(Context context, int packetDuration)
      throws YieldException {
    if (!CaptureAudio.hasPermission(context)) {
      if (CaptureAudio.requestPermission()) {
        throw new YieldException("waiting for audio recording permission");
      }
      State.log("skipping task, audio recording permission not granted");
      return true;
    }
    AudioRecord audioRecord =
        CaptureAudio.startPlaybackCapture(State.getMediaProjection(), SAMPLE_RATE, ENCODING);
    if (audioRecord != null) {
      SunshineServer.startAudioRecording(audioRecord, _framesPerPacket(packetDuration));
    }
    return false;
  }

  // samples per channel in packetDuration ms
  private static int _framesPerPacket(int packetDuration) {
    return (int) (SAMPLE_RATE * packetDuration / 1000.0f);
  }

  public static void restoreVolume(Context context) {
    if (submixHeld) {
      submixHeld = false;
      CaptureAudio.stopRemoteSubmix();
    }
    if (isMuted && context != null) {
      State.log("restoring volume");
      isMuted = false;
      AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
      audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);

      // unregister volume change listener
      if (volumeChangeListener != null) {
        audioManager.abandonAudioFocus(volumeChangeListener);
        volumeChangeListener = null;
      }
    }
  }
}
