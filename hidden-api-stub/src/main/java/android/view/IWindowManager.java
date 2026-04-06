package android.view;

import android.graphics.Point;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

import java.util.List;

public interface IWindowManager extends IInterface {
    abstract class Stub extends Binder implements IWindowManager {
        public static IWindowManager asInterface(IBinder obj)
        {
            throw new RuntimeException("Stub!");
        }
    }
    void getBaseDisplaySize(int displayId, Point size);
    void getInitialDisplaySize(int displayId, Point size);
    void setForcedDisplaySize(int displayId, int width, int height);
    void setForcedDisplayDensityForUser(int displayId, int density, int userId);
    void clearForcedDisplaySize(int displayId);
    void clearForcedDisplayDensityForUser(int displayId, int userId);
    int getDisplayUserRotation(int displayId);
    boolean isDisplayRotationFrozen(int displayId);
    void freezeDisplayRotation(int displayId, int rotation, String caller);
    void freezeDisplayRotation(int displayId, int rotation);
    void thawDisplayRotation(int displayId, String caller);
    void thawDisplayRotation(int displayId);
    void setFixedToUserRotation(int displayId, int fixedToUserRotation);
    void setIgnoreOrientationRequest(int displayId, boolean ignoreOrientationRequest);
    int getDisplayImePolicy(int displayId);
    void setDisplayImePolicy(int displayId, int imePolicy);
    int getImeDisplayId();
    void setRemoveContentMode(int displayId, int mode);
    int getWindowingMode(int displayId);
}
