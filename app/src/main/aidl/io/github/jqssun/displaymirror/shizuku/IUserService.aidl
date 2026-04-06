package io.github.jqssun.displaymirror.shizuku;

import android.view.Surface;

interface IUserService {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    String fetchLogs() = 2;

    String executeCommand(String command) = 3;

    boolean setScreenPower(int powerMode) = 4;

    void startListenVolumeKey() = 5;

    void stopListenVolumeKey() = 6;

    int createVirtualDisplay(in Surface surface) = 7;

    boolean isRooted() = 8;

    int readAudio(out float[] buffer) = 9;

    boolean startRecordingAudio() = 10;

    boolean stopRecordingAudio() = 11;
}