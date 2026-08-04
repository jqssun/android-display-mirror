package io.github.jqssun.displaymirror.job;

import android.content.Context;
import android.view.Surface;
import io.github.jqssun.displaymirror.MainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;

public class ProjectViaSunshine implements Job {
  private final int width;
  private final int height;
  private final int frameRate;
  private final int packetDuration;
  private final Surface surface;
  private final boolean shouldSendAudio;
  private boolean mediaProjectionRequested;

  public ProjectViaSunshine(
      int width,
      int height,
      int frameRate,
      int packetDuration,
      Surface surface,
      boolean shouldSendAudio) {
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
      State.log("client requested no audio capture, using phone speaker instead");
    }
    boolean rotateWithContent = Pref.getRotateWithContent();
    boolean cropBlackBorders = Pref.getCropBlackBorders();
    if (CreateVirtualDisplay.streamMirrors() && (rotateWithContent || cropBlackBorders)) {
      SunshineMouse.pipeline =
          new StreamRenderer(
              new VirtualDisplayArgs("Sunshine", width, height, frameRate, 160, false),
              rotateWithContent,
              cropBlackBorders,
              surface);
      SunshineMouse.pipeline.start(
          (input, w, h) -> {
            if (State.sunshineVirtualDisplay == null && State.getMediaProjection() != null) {
              State.setSunshineVirtualDisplay(
                  CreateVirtualDisplay.createForStream(
                      new VirtualDisplayArgs("Sunshine", w, h, frameRate, 160, false), input));
            } else if (State.sunshineVirtualDisplay != null) {
              State.sunshineVirtualDisplay.setSurface(input);
            }
            return State.sunshineVirtualDisplay;
          });
    } else {
      State.setSunshineVirtualDisplay(
          CreateVirtualDisplay.createForStream(
              new VirtualDisplayArgs(
                  "Sunshine", width, height, frameRate, 160, Pref.getRotateWithContent()),
              surface));
    }
  }

  private boolean _requestMediaProjectionPermission() throws YieldException {
    if (State.sunshineVirtualDisplay != null) {
      return true;
    }
    if (State.getMediaProjection() != null) {
      State.log("MediaProjection already exists, skipping duplicate request");
      return true;
    }
    if (mediaProjectionRequested) {
      State.log("skipping task, screen projection permission not granted");
      return false;
    }
    mediaProjectionRequested = true;
    MainActivity mirrorMainActivity = State.getCurrentActivity();
    if (mirrorMainActivity == null) {
      return false;
    }
    mirrorMainActivity.startMediaProjectionService();
    throw new YieldException("waiting for projection permission");
  }
}
