package com.displaylink.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.displaylink.manager.display.MonitorInfo;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.displaylink.DisplaylinkState;
import io.github.jqssun.displaymirror.displaylink.ProjectViaDisplaylink;
import io.github.jqssun.displaymirror.job.VirtualDisplayArgs;

public class NativeDriverListener {
  public NativeDriverListener() {}

  public void onDisplayConnected(long encoderId) {
    Log.i("displaylink", "onDisplayConnected");
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              State.log("display connected, Encoder ID: " + encoderId);
            });
  }

  public void onDisplayDisconnected(long encoderId) {
    Log.i("displaylink", "onDisplayDisconnected");
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              State.log("display disconnected, closing USB state");
              DisplaylinkState.instance.encoderId = 0;
              DisplaylinkState.instance.monitorInfo = null;
            });
  }

  public void onError(int i) {
    Log.i("displaylink", "onError: " + i);
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              State.log("DisplayLink reported error code: " + i);
            });
  }

  public void onFirmwareUpdateInfo(boolean z) {
    Log.i("displaylink", "onFirmwareUpdateInfo");
  }

  public void onUpdateMonitorInfo(long encoderId, MonitorInfo monitorInfo) {
    Log.i("displaylink", "onUpdateMonitorInfo");
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              State.log("onUpdateMonitorInfo: " + monitorInfo.toString());
              DisplaylinkState displaylinkState = DisplaylinkState.instance;
              boolean wasNoMonitor = displaylinkState.monitorInfo == null;
              displaylinkState.encoderId = encoderId;
              displaylinkState.monitorInfo = monitorInfo;
              Context context = State.getContext();
              if (!State.isJobRunning(DisplaylinkState.MODE) && wasNoMonitor && context != null) {
                displaylinkState.virtualDisplayArgs =
                    new VirtualDisplayArgs(
                        "DisplayLink",
                        Pref.getDisplaylinkWidth(),
                        Pref.getDisplaylinkHeight(),
                        Pref.getDisplaylinkRefreshRate(),
                        160,
                        Pref.getRotateWithContent());
                State.startNewJob(
                    DisplaylinkState.MODE,
                    new ProjectViaDisplaylink(
                        displaylinkState.device, displaylinkState.virtualDisplayArgs));
              }
            });
  }
}
