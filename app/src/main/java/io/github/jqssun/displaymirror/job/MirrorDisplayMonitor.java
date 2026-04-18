package io.github.jqssun.displaymirror.job;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.IAudioService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

import io.github.jqssun.displaymirror.MirrorMainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.SunshineService;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import java.util.List;

public class MirrorDisplayMonitor {
    private static boolean registered = false;
    public static void init(DisplayManager displayManager) {
        for (Display display : displayManager.getDisplays()) {
            _handleNewDisplay(display);
        }
        if (registered) {
            return;
        }
        registered = true;
        displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                State.log("Display added, displayId: " + displayId);
                Display display = displayManager.getDisplay(displayId);
                if (display != null) {
                    _handleNewDisplay(display);
                }
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (SunshineService.instance == null) {
                        return;
                    }
                    String targetScreen = _resolveTargetScreen(displayId);
                    if (State.getCurrentActivity() != null) {
                        State.getCurrentActivity().finish();
                    }
                    Intent intent = new Intent(SunshineService.instance, MirrorMainActivity.class);
                    intent.setAction(MirrorMainActivity.ACTION_OPEN_SCREEN);
                    intent.putExtra(MirrorMainActivity.EXTRA_SCREEN, targetScreen);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ActivityOptions options = ActivityOptions.makeBasic();
                    options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
                    SunshineService.instance.startActivity(intent, options.toBundle());
                }, 1000);
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                State.log("Display removed, displayId: " + displayId);
                if (displayId == State.lastSingleAppDisplay) {
                    State.clearLastSingleAppDisplay();
                }
            }

            @Override
            public void onDisplayChanged(int displayId) {
            }
        }, null);
    }

    private static void _handleNewDisplay(Display display) {
        if (display.getDisplayId() == Display.DEFAULT_DISPLAY) {
            return;
        }
        if (display.getDisplayId() == State.getDisplaylinkVirtualDisplayId()) {
            return;
        }
        if (display.getDisplayId() == State.getMirrorVirtualDisplayId()) {
            return;
        }
        if ("Moonlight".equals(display.getName())) {
            return;
        }
        if ("DisplayLink".equals(display.getName())) {
            return;
        }
        if ("Mirror".equals(display.getName())) {
            return;
        }
        if (CreateVirtualDisplay.isCreating) {
            return;
        }
        Context context = State.getContext();
        if (context == null) {
            return;
        }
        State.startNewJob(State.MODE_MIRROR, new ProjectViaMirror(display));
        _handleDisableUsbAudio(context);
    }

    private static String _resolveTargetScreen(int displayId) {
        MirrorMainActivity currentActivity = State.getCurrentActivity();
        if (currentActivity != null) {
            String currentScreen = currentActivity.getCurrentScreen();
            if (MirrorMainActivity.SCREEN_MOONLIGHT.equals(currentScreen)
                    || MirrorMainActivity.SCREEN_AIRPLAY.equals(currentScreen)
                    || MirrorMainActivity.SCREEN_DISPLAYLINK.equals(currentScreen)) {
                return currentScreen;
            }
        }

        if (displayId == State.getDisplaylinkVirtualDisplayId()) {
            return MirrorMainActivity.SCREEN_DISPLAYLINK;
        }
        if (displayId == State.getAirPlayVirtualDisplayId()) {
            return MirrorMainActivity.SCREEN_AIRPLAY;
        }
        if (displayId == State.getMirrorVirtualDisplayId() || displayId == State.lastSingleAppDisplay) {
            return MirrorMainActivity.SCREEN_MOONLIGHT;
        }
        return MirrorMainActivity.SCREEN_MOONLIGHT;
    }
    
    private static void _handleDisableUsbAudio(Context context) {
        if (!ShizukuUtils.hasPermission()) {
            return;
        }
        boolean isDisabled = Pref.getDisableUsbAudio();
        if (!isDisabled) {
            return;
        }
        IAudioService audioManager = ServiceUtils.getAudioManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            List<AudioDeviceAttributes> devices = audioManager.getDevicesForAttributes(new AudioAttributes.Builder().build());
            for (AudioDeviceAttributes device : devices) {
                if (device.getType() == AudioDeviceInfo.TYPE_HDMI) {
                    try {
                        audioManager.setWiredDeviceConnectionState(device, 0, "com.android.shell");
                        State.log("Disabled audio output device: " + device);
                    } catch(Throwable e) {
                        State.log("Failed to disable audio output device: " + e);
                    }
                }
            }
        } else {
            AudioManager audioManager2 = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            for (AudioDeviceInfo device : audioManager2.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getType() == AudioDeviceInfo.TYPE_HDMI) {
                    try {
                        audioManager.setWiredDeviceConnectionState(device.getType(), 0, device.getAddress(), "", "com.android.shell");
                        State.log("Disabled audio output device: " + device.getType() + ", " + device.getProductName());
                    } catch(Throwable e) {
                        State.log("Failed to disable audio output device: " + e);
                    }
                } else if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    try {
                        audioManager.setWiredDeviceConnectionState(device.getType(), 1, device.getAddress(), "", "com.android.shell");
                        State.log("Enabled audio output device: " + device.getType() + ", " + device.getProductName());
                    } catch(Throwable e) {
                        State.log("Failed to enable audio output device: " + e);
                    }
                }
            }
        }
    }
}
