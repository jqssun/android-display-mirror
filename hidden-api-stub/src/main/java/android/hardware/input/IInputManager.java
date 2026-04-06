package android.hardware.input;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.view.InputDevice;
import android.view.InputEvent;

public interface IInputManager extends IInterface {
    abstract class Stub extends Binder implements IInputManager {
        public static IInputManager asInterface(IBinder obj)
        {
            throw new RuntimeException("Stub!");
        }
    }
    InputDevice getInputDevice(int deviceId);
    int[] getInputDeviceIds();
    boolean injectInputEvent(InputEvent ev, int mode);
    void addPortAssociation(String inputPort, int displayPort);
    void removePortAssociation(String inputPort);
    void addUniqueIdAssociation(String inputPort, String displayUniqueId);
    void removeUniqueIdAssociation(String inputPort);
    void addUniqueIdAssociationByPort(String inputPort, String displayUniqueId);
    void addUniqueIdAssociationByDescriptor(String inputDeviceDescriptor, String displayUniqueId);
    void removeUniqueIdAssociationByDescriptor(String inputDeviceDescriptor);
}
