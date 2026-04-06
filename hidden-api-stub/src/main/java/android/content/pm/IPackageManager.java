package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IPackageManager extends IInterface {
    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder obj)
        {
            throw new RuntimeException("Stub!");
        }
    }
    void grantRuntimePermission(String packageName, String permissionName, int userId);
    String[] getPackagesForUid(int uid) throws RemoteException;
    PackageInfo getPackageInfo(String packageName, long flags, int userId) throws android.os.RemoteException;
    PackageInfo getPackageInfo(String packageName, int flags, int userId) throws android.os.RemoteException;
}
