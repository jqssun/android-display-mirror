package io.github.jqssun.displaymirror.job;

import static io.github.jqssun.displaymirror.MirrorMainActivity.REQUEST_RECORD_AUDIO_PERMISSION;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;

import androidx.core.app.ActivityCompat;

import io.github.jqssun.displaymirror.MirrorMainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;

public class SunshineAudio {
    private static boolean audioPermissionRequested;
    private static boolean isMuted = false;
    private static AudioManager.OnAudioFocusChangeListener volumeChangeListener;
    public static boolean sendAudio(Context context, int packetDuration) throws YieldException {
        if (shouldUseShizukuAudio()) {
            int framesPerPacket = (int) (48000 * packetDuration / 1000.0f);
            AudioRecordProxy audioRecordProxy = new AudioRecordProxy();
            if (!startRecording()) {
                State.log("Failed to start audio recording");
                return true;
            }
            SunshineServer.startAudioRecording(audioRecordProxy, framesPerPacket);
        } else {
            if (sendAudioUseNormalPermission(context, packetDuration)) {
                return true;
            }
            // check audio settings permission
            if (context.checkSelfPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                State.log("No audio control permission, cannot mute");
            }
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
            if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                isMuted = true;
                State.log("Muting phone audio at client's request");
                // register volume change listener
                registerVolumeChangeListener(context, audioManager);
            } else {
                State.log("Failed to set mute");
            }
            return false;
        }
        return false;
    }

    // register volume change listener method
    private static void registerVolumeChangeListener(Context context, AudioManager audioManager) {

        // create audio focus change listener
        volumeChangeListener = focusChange -> {
            // if still projecting and should stay muted, check and re-mute
            if (State.mirrorVirtualDisplay != null && isMuted) {
                checkAndRestoreMute();
            }
        };

        // request audio focus to receive audio change events
        audioManager.requestAudioFocus(volumeChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        // create content observer to listen for volume changes
        context.getContentResolver().registerContentObserver(
                android.provider.Settings.System.CONTENT_URI,
                true,
                new android.database.ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        super.onChange(selfChange);
                        // if still projecting and should stay muted, check and re-mute
                        if (State.mirrorVirtualDisplay != null && isMuted) {
                            checkAndRestoreMute();
                        }
                    }
                }
        );
    }

    // check and restore mute state
    private static void checkAndRestoreMute() {
        Context context = State.getContext();
        if (context == null) {
            return;
        }
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (!audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
            State.log("Volume change detected, reapplying mute");
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
        }
    }

    private static boolean shouldUseShizukuAudio() {
        if (Pref.getDisableRemoteSubmix()) {
            return false;
        }
        return State.userService != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S;
    }

    private static boolean startRecording() {
        try {
            return State.userService.startRecordingAudio();
        } catch (RemoteException e) {
            return false;
        }
    }

    private static boolean sendAudioUseNormalPermission(Context context, int packetDuration) throws YieldException {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            State.log("Android version too low for audio recording");
            return false;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // configure audio capture parameters
            int sampleRate = 48000; // match Opus configuration
            int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
            int audioEncoding = AudioFormat.ENCODING_PCM_FLOAT;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding) * 2;

            // calculate number of frames per packet (number of samples per channel)
            // packetDuration is in ms
            int framesPerPacket = (int) (sampleRate * packetDuration / 1000.0f);
            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setEncoding(audioEncoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build();
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(State.getMediaProjection())
                    .excludeUsage(AudioAttributes.USAGE_ALARM)
                    .build();
            AudioRecord audioRecord = new AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build();
            audioRecord.startRecording();

            // pass AudioRecord to SunshineServer for processing
            SunshineServer.startAudioRecording(audioRecord, framesPerPacket);

        } else {
            if (audioPermissionRequested) {
                State.log("Skipping task, audio recording permission not granted");
                return true;
            }
            audioPermissionRequested = true;
            MirrorMainActivity activity = State.getCurrentActivity();
            if (activity == null) {
                return true;
            }
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
            throw new YieldException("Waiting for audio recording permission");
        }
        return false;
    }

    public static void restoreVolume(Context context) {
        if (State.userService != null) {
            try {
                State.userService.stopRecordingAudio();
            } catch (RemoteException e) {
                // ignore
            }
        }
        if (isMuted && context != null) {
            State.log("Restoring volume");
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
