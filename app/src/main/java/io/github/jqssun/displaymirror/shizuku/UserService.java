package io.github.jqssun.displaymirror.shizuku;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.IDisplayManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.system.Os;
import android.util.Log;

import android.os.RemoteException;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import io.github.jqssun.displaymirror.job.AndroidVersions;
import io.github.jqssun.displaymirror.job.CreateVirtualDisplay;

import rikka.shizuku.SystemServiceHelper;

public class UserService extends IUserService.Stub  {
    private Context context;
    private boolean listenVolumeKey = false;
    private Process listenVolumeKeyProcess;
    private Thread volumeKeyThread;
    private AudioRecord audioRecord;
    private float[] buffer;

    public UserService() {
        _dropToShellIfRoot();
        Ln.i("Start UserService without context: " + android.os.Process.myUid());
    }

    @Keep
    public UserService(Context context) {
        this.context = context;
        _dropToShellIfRoot();
        Ln.i("Start UserService with context: " + android.os.Process.myUid());
    }

    private void _dropToShellIfRoot() {
        int uid = android.os.Process.myUid();
        if (uid != 0) return;
        int shellUid = android.os.Process.SHELL_UID;
        try {
            Os.setuid(shellUid);
            Ln.i("Dropped root to shell UID " + shellUid + " for trusted display compatibility");
        } catch (Exception e) {
            Ln.e("Failed to drop root to shell UID " + shellUid, e);
        }
    }

    /**
     * Reserved destroy method
     */
    @Override
    public void destroy() {
        Log.i("UserService", "destroy");
        stopListenVolumeKey();
        setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
        if (audioRecord != null) {
            audioRecord.stop();
        }
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public String fetchLogs() throws RemoteException {
        return executeCommand("logcat -d");
    }

    @Override
    public String executeCommand(String command) throws RemoteException {
        Process process = null;
        try {
            process = new ProcessBuilder(command.split("\\s+"))
                    .redirectErrorStream(true)
                    .start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            reader.close();
            process.waitFor();
            return output.toString();
        } catch (Exception e) {
            Log.e("UserService", "execute command failed: " + command, e);
            throw new RemoteException("Failed to execute command: " + command + " " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    public boolean setScreenPower(int powerMode) {
        Log.i("UserService", "try to setScreenPower: " + powerMode);
        if (Build.VERSION.SDK_INT >= 35 && powerMode == SurfaceControl.POWER_MODE_NORMAL) {
            setScreenPowerViaNewApi(SurfaceControl.POWER_MODE_OFF);
            setScreenPowerViaNewApi(SurfaceControl.POWER_MODE_NORMAL);
        }
        try {
            IBinder displayToken = getDisplayToken();
            if (displayToken == null) {
                return false;
            }
            Ln.d("setDisplayPowerMode: " + displayToken + " " + powerMode);
            boolean result = SurfaceControl.setDisplayPowerMode(displayToken, powerMode);
            Ln.d("after setDisplayPowerMode: " + result);
        } catch(Throwable e) {
            Ln.e("setScreenPower failed", e);
        }
        return true;
    }

    private boolean setScreenPowerViaNewApi(int powerMode) {
        IDisplayManager displayManager = IDisplayManager.Stub.asInterface(SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE));
        if (powerMode == SurfaceControl.POWER_MODE_OFF) {
            try {
                displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, false);
                Log.i("UserService", "requestDisplayPower by bool false");
            } catch(Throwable e) {
                Log.e("UserService", "failed to power off screen", e);
                try {
                    displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_OFF);
                    Log.i("UserService", "requestDisplayPower by int: " + powerMode);
                } catch(Throwable e2) {
                    Log.e("UserService", "failed to power off screen", e2);
                    return false;
                }
            }
        } else {
            try {
                displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, true);
                Log.i("UserService", "requestDisplayPower by bool true");
            } catch (Throwable e) {
                Log.e("UserService", "failed to power up screen", e);
                try {
                    displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_NORMAL);
                    Log.i("UserService", "requestDisplayPower by int: " + powerMode);
                } catch(Throwable e2) {
                    Log.e("UserService", "failed to power up screen", e2);
                    return false;
                }
            }
        }
        return true;
    }

    private @Nullable IBinder getDisplayToken() {
        try {
            long[] physicalDisplayIds = DisplayControl.getPhysicalDisplayIds();
            Ln.d("physicalDisplayIds count: " + physicalDisplayIds.length);
            if (physicalDisplayIds.length > 0) {
                return DisplayControl.getPhysicalDisplayToken(physicalDisplayIds[0]);
            }
            return SurfaceControl.getBuiltInDisplay();
        } catch (Throwable e) {
            Ln.e("failed to getDisplayToken", e);
            try {
                return SurfaceControl.getBuiltInDisplay();
            } catch (Throwable e2) {
                Ln.e("failed to getDisplayToken", e2);
            }
        }
        return null;
    }

    public void startListenVolumeKey() throws RemoteException {
        if (listenVolumeKey) {
            return;
        }
        listenVolumeKey = true;
        Thread thread = new Thread(() -> {
            while(listenVolumeKey) {
                try {
                    Ln.i("Run getevent to detect volume key pressed");
                    listenVolumeKeyProcess = Runtime.getRuntime().exec("getevent");
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(listenVolumeKeyProcess.getInputStream()));
                    while (listenVolumeKey) {
                        String line = reader.readLine();
                        if (line == null || !listenVolumeKey) {
                            Ln.i("break out getevent");
                            break;
                        }
                        if (!line.endsWith("0000 0000 00000000") &&
                                (line.endsWith("0001 0072 00000001") || line.endsWith("0001 0073 00000001"))) {
                            Ln.i("detected volume key, try to power on screen");
                            setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
                            if (context != null) {
                                Intent intent = new Intent("io.github.jqssun.displaymirror.EXIT_PURE_BLACK");
                                intent.setPackage("io.github.jqssun.displaymirror");
                                context.sendBroadcast(intent);
                            } else {
                                Ln.i("context is null, can not send EXIT_PURE_BLACK");
                            }
                        }
                    }
                    reader.close();
                    if (listenVolumeKeyProcess != null) {
                        listenVolumeKeyProcess.waitFor();
                        listenVolumeKeyProcess.destroyForcibly();
                    }
                } catch (Exception e) {
                    Ln.e("Listen volume key failed", e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
            Ln.i("getevent thread end");
        });
        volumeKeyThread = thread;
        thread.start();
    }

    public void stopListenVolumeKey() {
        listenVolumeKey = false;
        if (listenVolumeKeyProcess != null) {
            listenVolumeKeyProcess.destroyForcibly();
            listenVolumeKeyProcess = null;
        }
        if (volumeKeyThread != null) {
            volumeKeyThread.interrupt();
            volumeKeyThread = null;
        }
    }

    @Override
    public int createVirtualDisplay(Surface surface) throws RemoteException {
        Ln.i("try to createVirtualDisplay");
        try {
            return DisplayManager.create().createNewVirtualDisplay("test", 1920, 1080, 160, surface, CreateVirtualDisplay.getFlags(true, true)).getDisplay().getDisplayId();
        } catch (Throwable e) {
            Ln.e("failed to create virtual display", e);
        }
        return 0;
    }

    @Override
    public boolean isRooted() throws RemoteException {
        return Os.getuid() == 0;
    }

    @Override
    public int readAudio(float[] result) throws RemoteException {
        try {
            if (audioRecord == null) {
                return 0;
            }
            return audioRecord.read(result, 0, result.length, AudioRecord.READ_BLOCKING);
        } catch(Throwable e) {
            Ln.e("failed to read audio", e);
            return 0;
        }
    }

    @Override
    public boolean startRecordingAudio() throws RemoteException {
        try {
            if (audioRecord == null) {
                Ln.d("before start recording");
                audioRecord = createAudioRecord();
                audioRecord.startRecording();
                Ln.d("started recording");
                return true;
            } else {
                return true;
            }
        } catch(Throwable e) {
            Ln.e("failed to start recording audio", e);
            return false;
        }
    }

    @Override
    public boolean stopRecordingAudio() throws RemoteException {
        try {
            if (audioRecord == null) {
                return true;
            } else {
                audioRecord.stop();
                audioRecord = null;
                return true;
            }
        } catch(Throwable e) {
            Ln.e("failed to stop recording audio", e);
            return false;
        }
    }

    @SuppressLint({"WrongConstant", "MissingPermission"})
    private AudioRecord createAudioRecord() {
        AudioRecord.Builder builder = new AudioRecord.Builder();
        if (Build.VERSION.SDK_INT >= AndroidVersions.API_31_ANDROID_12) {
            // On older APIs, Workarounds.fillAppInfo() must be called beforehand
            builder.setContext(context);
        }
        builder.setAudioSource(MediaRecorder.AudioSource.REMOTE_SUBMIX);
        int sampleRate = 48000; // match Opus configuration
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        int audioEncoding = AudioFormat.ENCODING_PCM_FLOAT;
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(audioEncoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build();
        builder.setAudioFormat(audioFormat);
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding);
        if (minBufferSize > 0) {
            // This buffer size does not impact latency
            builder.setBufferSizeInBytes(2 * minBufferSize);
        }
        return builder.build();
    }
}
