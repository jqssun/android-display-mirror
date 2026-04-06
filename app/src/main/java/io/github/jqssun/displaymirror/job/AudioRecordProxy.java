package io.github.jqssun.displaymirror.job;

import android.os.RemoteException;

import io.github.jqssun.displaymirror.State;

public class AudioRecordProxy {
    public int read(float[] audioData, int offsetInFloats, int sizeInFloats, int readMode) {
        try {
            if (State.userService != null) {
                return State.userService.readAudio(audioData);
            } else {
                return 0;
            }
        } catch (RemoteException e) {
            return 0;
        }
    }
}
