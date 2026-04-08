package io.github.jqssun.displaymirror.job;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Surface;

import io.github.jqssun.displaymirror.FloatingButtonService;
import io.github.jqssun.displaymirror.MirrorMainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

public class ProjectViaMoonlight implements Job {
    private final int width;
    private final int height;
    private final int frameRate;
    private final int packetDuration;
    private final Surface surface;
    private final boolean shouldSendAudio;
    private boolean mediaProjectionRequested;

    public ProjectViaMoonlight(int width, int height, int frameRate, int packetDuration, Surface surface, boolean shouldSendAudio) {
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.packetDuration = packetDuration;
        this.surface = surface;
        this.shouldSendAudio = shouldSendAudio;
    }

    @Override
    public void start() throws YieldException {
        if (!_requestMediaProjectionPermission()) {
            return;
        }
        Context context = State.getContext();
        if (context == null) {
            return;
        }
        if (shouldSendAudio) {
            if (SunshineAudio.sendAudio(context, packetDuration)) {
                return;
            }
        } else {
            State.log("Client requested no audio capture, using phone speaker instead");
        }
        boolean autoRotate = Pref.getAutoRotate();
        boolean autoScale = Pref.getAutoScale();
        boolean singleAppMode = Pref.getSingleAppMode();
        if (singleAppMode) {
            if (ShizukuUtils.hasPermission()) {
                int singleAppDpi = Pref.getSingleAppDpi();
                State.mirrorVirtualDisplay = CreateVirtualDisplay.createVirtualDisplay(new VirtualDisplayArgs("Moonlight",
                        width, height, frameRate, singleAppDpi, autoRotate), surface);
                String selectedAppPackage = Pref.getSelectedAppPackage();
                ServiceUtils.launchPackage(context, selectedAppPackage, State.mirrorVirtualDisplay.getDisplay().getDisplayId());
                InputRouting.bindAllExternalInputToDisplay(State.mirrorVirtualDisplay.getDisplay().getDisplayId());
                InputRouting.moveImeToExternal(State.mirrorVirtualDisplay.getDisplay().getDisplayId());
            } else {
                State.showErrorStatus("Moonlight single-app projection requires Shizuku permission");
            }
        } else if (autoRotate || autoScale) {
            SunshineMouse.autoRotateAndScaleForMoonlight = new AutoRotateAndScaleForMoonlight(new VirtualDisplayArgs("Moonlight",
                    width, height, frameRate, 160, false));
            SunshineMouse.autoRotateAndScaleForMoonlight.start(surface);
            CreateVirtualDisplay.powerOffScreen();
        } else {
            State.mirrorVirtualDisplay = State.getMediaProjection().createVirtualDisplay("Moonlight",
                    width,
                    height,
                    160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    surface, null, null);
            State.setMediaProjection(null);
            FloatingButtonService.startForMirror();
            CreateVirtualDisplay.powerOffScreen();
        }
    }


    private boolean _requestMediaProjectionPermission() throws YieldException {
        if (State.mirrorVirtualDisplay != null) {
            return true;
        }
        if (State.getMediaProjection() != null) {
            State.log("MediaProjection already exists, skipping duplicate request");
            return true;
        }
        if (mediaProjectionRequested) {
            State.log("Skipping task, screen projection permission not granted");
            return false;
        }
        mediaProjectionRequested = true;
        MirrorMainActivity mirrorMainActivity = State.getCurrentActivity();
        if (mirrorMainActivity == null) {
            return false;
        }
        mirrorMainActivity.startMediaProjectionService();
        throw new YieldException("Waiting for projection permission");
    }
}
