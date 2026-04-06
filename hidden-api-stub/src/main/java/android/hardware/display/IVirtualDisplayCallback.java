package android.hardware.display;

import android.os.Binder;
import android.os.IBinder;

public interface IVirtualDisplayCallback {
    abstract class Stub extends Binder implements IVirtualDisplayCallback {
        public static IVirtualDisplayCallback asInterface(IBinder obj)
        {
            throw new RuntimeException("Stub!");
        }
    }
}
