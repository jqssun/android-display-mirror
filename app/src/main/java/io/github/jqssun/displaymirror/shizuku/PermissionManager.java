package io.github.jqssun.displaymirror.shizuku;

import android.content.pm.IPackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserHandleHidden;
import android.permission.IPermissionManager;
import dev.rikka.tools.refine.Refine;
import io.github.jqssun.displaymirror.BuildConfig;
import io.github.jqssun.displaymirror.State;

public class PermissionManager {
  public static boolean grant(String permissionName) {
    try {
      return _grant(permissionName);
    } catch (Throwable e) {
      State.log("grant failed: " + e);
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
      packageManager.grantRuntimePermission(
          packageName, permissionName, userHandleHidden.getIdentifier());
      State.log("granted " + permissionName);
      return true;
    } else {
      try {
        permissionManager.grantRuntimePermission(
            packageName, permissionName, "0", userHandleHidden.getIdentifier());
        State.log("granted " + permissionName);
        return true;
      } catch (Throwable e) {
        try {
          permissionManager.grantRuntimePermission(
              packageName, permissionName, userHandleHidden.getIdentifier());
          State.log("granted " + permissionName);
          return true;
        } catch (Throwable e2) {
          State.log("grant failed: " + e2.getMessage());
        }
      }
    }
    return false;
  }
}
