package io.github.jqssun.displaymirror.sunshine;

import android.content.Context;
import android.view.Surface;
import io.github.jqssun.displaymirror.MainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.job.CreateVirtualDisplay;
import io.github.jqssun.displaymirror.job.Job;
import io.github.jqssun.displaymirror.job.StreamRenderer;
import io.github.jqssun.displaymirror.job.VirtualDisplayArgs;
import io.github.jqssun.displaymirror.job.YieldException;

public class ProjectViaSunshine implements Job {
  private final long session;
  private final int width;
  private final int height;
  private final int frameRate;
  private final int packetDuration;
  private final Surface surface;
  private final boolean shouldSendAudio;
  private boolean mediaProjectionRequested;

  public ProjectViaSunshine(
      long session,
      int width,
      int height,
      int frameRate,
      int packetDuration,
      Surface surface,
      boolean shouldSendAudio) {
    this.session = session;
    this.width = width;
    this.height = height;
    this.frameRate = frameRate;
    this.packetDuration = packetDuration;
    this.surface = surface;
    this.shouldSendAudio = shouldSendAudio;
  }

  @Override
  public void start() throws YieldException {
    Context context = State.getContext();
    if (context == null) {
      return;
    }
    boolean mirrors = CreateVirtualDisplay.streamMirrors();
    if (mirrors && SunshineState.pipeline != null) {
      // later sessions join shared capture, no projection needed
      if (_sendAudio(context)) {
        return;
      }
      SunshineState.addSession(session);
      SunshineState.pipeline.addOutput(session, surface, width, height);
      return;
    }
    if (!_requestMediaProjectionPermission()) {
      return;
    }
    if (_sendAudio(context)) {
      return;
    }
    if (mirrors) {
      StreamRenderer pipeline =
          new StreamRenderer(
              new VirtualDisplayArgs("Sunshine", width, height, frameRate, 160, false),
              Pref.getRotateWithContent(),
              Pref.getCropBlackBorders());
      SunshineState.pipeline = pipeline;
      SunshineState.addSession(session);
      pipeline.addOutput(session, surface, width, height);
      pipeline.start(
          (input, w, h) -> {
            if (SunshineState.virtualDisplay == null && State.getMediaProjection() != null) {
              SunshineState.setVirtualDisplay(
                  CreateVirtualDisplay.createForStream(
                      new VirtualDisplayArgs("Sunshine", w, h, frameRate, 160, false), input));
            } else if (SunshineState.virtualDisplay != null) {
              SunshineState.virtualDisplay.setSurface(input);
            }
            return SunshineState.virtualDisplay;
          });
    } else {
      SunshineState.addSessionDisplay(
          session,
          CreateVirtualDisplay.createForStream(
              new VirtualDisplayArgs(
                  "Sunshine", width, height, frameRate, 160, Pref.getRotateWithContent()),
              surface));
    }
  }

  // true when the job should abort
  private boolean _sendAudio(Context context) throws YieldException {
    if (!shouldSendAudio) {
      State.log("client requested no audio capture, using phone speaker instead");
      return false;
    }
    return SunshineAudio.sendAudio(context, packetDuration);
  }

  private boolean _requestMediaProjectionPermission() throws YieldException {
    if (SunshineState.virtualDisplay != null) {
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
