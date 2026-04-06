package android.hardware.display;

import android.media.projection.IMediaProjection;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Surface;

public interface IDisplayManager extends IInterface {
    abstract class Stub extends Binder implements IDisplayManager {
        public static IDisplayManager asInterface(IBinder obj)
        {
            throw new RuntimeException("Stub!");
        }
    }
    void setUserPreferredDisplayMode(int displayId, Display.Mode mode);
    DisplayInfo getDisplayInfo(int displayId);
    int createVirtualDisplay(VirtualDisplayConfig config, IVirtualDisplayCallback callback, IMediaProjection mediaProjection, String packageName);
    int createVirtualDisplay(IVirtualDisplayCallback callback,
                             IMediaProjection projectionToken, String packageName, String name,
                             int width, int height, int densityDpi, Surface surface, int flags, String uniqueId);
    boolean requestDisplayPower(int displayId, int state);
    boolean requestDisplayPower(int displayId, boolean state);
}
