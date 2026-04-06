package android.media;

import android.media.audiopolicy.AudioProductStrategy;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.UserHandle;

import java.util.List;

public interface IAudioService extends IInterface {
    abstract class Stub extends Binder implements IAudioService {
        public static IAudioService asInterface(IBinder obj)
        {
            throw new RuntimeException("Stub!");
        }
    }
    void setWiredDeviceConnectionState(int type, int state, String address, String name,
                                       String caller);
    void setWiredDeviceConnectionState(AudioDeviceAttributes aa, int state, String caller);
    List<AudioDeviceAttributes> getDevicesForAttributes(AudioAttributes attributes);
    List<AudioProductStrategy> getAudioProductStrategies();
    List<AudioDeviceAttributes> getPreferredDevicesForStrategy(int strategy);
    int setHdmiSystemAudioSupported(boolean on);
    boolean isHdmiSystemAudioSupported();
    boolean isStreamMute(int streamType);
    void adjustStreamVolumeForUid(int streamType, int direction, int flags,
                                  String packageName, int uid, int pid, UserHandle userHandle,
                                  int targetSdkVersion);
}
