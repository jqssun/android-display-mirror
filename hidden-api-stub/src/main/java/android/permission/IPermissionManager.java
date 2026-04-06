package android.permission;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

public interface IPermissionManager extends IInterface {
    abstract class Stub extends Binder implements IPermissionManager {
        public static IPermissionManager asInterface(IBinder obj)
        {
            throw new RuntimeException("Stub!");
        }
    }
    void grantRuntimePermission(String packageName, String permissionName,
                                String persistentDeviceId, int userId);
    void grantRuntimePermission(String packageName, String permissionName, int userId);
}
