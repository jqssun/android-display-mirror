package io.github.jqssun.displaymirror.shizuku;

import android.os.ParcelFileDescriptor;

interface IUserService {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    void fetchLogs(in ParcelFileDescriptor sink) = 2;

    String executeCommand(String command) = 3;

    boolean setScreenPower(int powerMode) = 4;

    void startListenVolumeKey() = 5;

    void stopListenVolumeKey() = 6;

    boolean isRooted() = 8;

    int readAudioFloat(out float[] buffer) = 9;

    boolean startRecordingAudio(int sampleRate, int encoding) = 10;

    boolean stopRecordingAudio() = 11;

    int readAudioPcm16(out byte[] buffer) = 12;

    // sockets owned by the shell UID and bound to the LAN interface, so a non-bypassable VPN on the app UID doesn't swallow them; null when no interface reaches host
    ParcelFileDescriptor lanDialTcp(String host, int port, int timeoutMs) = 13;

    ParcelFileDescriptor lanListenUdp(String host, int port) = 14;
}