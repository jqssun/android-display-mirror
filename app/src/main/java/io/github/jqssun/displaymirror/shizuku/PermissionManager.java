package io.github.jqssun.displaymirror.shizuku;

import android.content.pm.IPackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserHandleHidden;
import android.permission.IPermissionManager;
import android.util.Log;


import io.github.jqssun.displaymirror.BuildConfig;
import io.github.jqssun.displaymirror.State;

import dev.rikka.tools.refine.Refine;

public class PermissionManager {
    public static boolean grant(String permissionName) {
        try {
            return _grant(permissionName);
        } catch(Throwable e) {
            State.log("Authorization failed: " + e);
            return false;
        }
    }
    private static boolean _grant(String permissionName) {
        UserHandle userHandle = Process.myUserHandle();
        UserHandleHidden userHandleHidden = Refine.unsafeCast(userHandle);
        String packageName = BuildConfig.APPLICATION_ID;
        IPermissionManager permissionManager = ServiceUtils.getPermissionManager();
        if (permissionManager == null) {
            IPackageManager packageManager = ServiceUtils.getPackageManager();
            packageManager.grantRuntimePermission(packageName, permissionName, userHandleHidden.getIdentifier());
            Log.i("PermissionManager", "Successfully granted " + permissionName + " permission");
            return true;
        } else {
            try {
                permissionManager.grantRuntimePermission(
                        packageName,
                        permissionName,
                        "0", userHandleHidden.getIdentifier());
                Log.i("PermissionManager", "Successfully granted " + permissionName + " permission");
                return true;
            } catch (Throwable e) {
                try {
                    permissionManager.grantRuntimePermission(
                            packageName,
                            permissionName,
                            userHandleHidden.getIdentifier());
                    Log.i("PermissionManager", "Successfully granted " + permissionName + " permission");
                    return true;
                } catch (Throwable e2) {
                    State.log("Failed to grant permission: " + e2.getMessage());
                }
            }
        }
        return false;
    }
}
