package io.github.jqssun.displaymirror.shizuku;

import android.app.IActivityTaskManager;
import android.content.Context;
import android.hardware.display.IDisplayManager;
import android.hardware.input.IInputManager;
import android.media.IAudioService;
import android.permission.IPermissionManager;
import android.content.pm.IPackageManager;
import android.view.IWindowManager;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

public class ServiceUtils {
    private static IWindowManager windowManager;
    private static IDisplayManager displayManager;
    private static IInputManager inputManager;
    private static IPermissionManager permissionManager;
    private static IPackageManager packageManager;
    private static IAudioService audioManager;
    private static IActivityTaskManager activityTaskManager;

    public static void invalidate() {
        windowManager = null;
        displayManager = null;
        inputManager = null;
        permissionManager = null;
        packageManager = null;
        audioManager = null;
        activityTaskManager = null;
    }

    /** Pre-cache all binders on the main thread so they work from any thread. */
    public static void ensureInitialized() {
        _init();
    }

    private static void _init() {
        if (!ShizukuUtils.hasPermission()) return;
        windowManager = IWindowManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.WINDOW_SERVICE)));
        displayManager = IDisplayManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE)));
        inputManager = IInputManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.INPUT_SERVICE)));
        try {
            permissionManager = IPermissionManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("permissionmgr")));
        } catch (Throwable e) { /* ignore */ }
        packageManager = IPackageManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")));
        audioManager = IAudioService.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.AUDIO_SERVICE)));
        activityTaskManager = IActivityTaskManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity_task")));
    }

    public static IWindowManager getWindowManager() {
        if (windowManager == null) _init();
        return windowManager;
    }

    public static IDisplayManager getDisplayManager() {
        if (displayManager == null) _init();
        return displayManager;
    }

    public static IInputManager getInputManager() {
        if (inputManager == null) _init();
        return inputManager;
    }

    public static IPermissionManager getPermissionManager() {
        if (permissionManager == null) _init();
        return permissionManager;
    }

    public static IPackageManager getPackageManager() {
        if (packageManager == null) _init();
        return packageManager;
    }

    public static IAudioService getAudioManager() {
        if (audioManager == null) _init();
        return audioManager;
    }

    public static IActivityTaskManager getActivityTaskManager() {
        if (activityTaskManager == null) _init();
        return activityTaskManager;
    }
}
