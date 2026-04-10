package io.github.jqssun.displaymirror.shizuku;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityOptionsHidden;
import android.app.PendingIntentHidden;
import android.content.ComponentName;
import android.app.IActivityManager;
import android.app.IActivityTaskManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.IDisplayManager;
import android.hardware.input.IInputManager;
import android.media.IAudioService;
import android.os.Build;
import android.permission.IPermissionManager;
import android.view.Display;
import android.view.IWindowManager;
import android.widget.Toast;

import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.job.BindAllExternalInputToDisplay;

import dev.rikka.tools.refine.Refine;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

import java.util.List;

public class ServiceUtils {
    private static final int WINDOWING_MODE_FULLSCREEN = 1;

    private static IActivityManager activityManager;
    private static IActivityTaskManager activityTaskManager;
    private static IWindowManager windowManager;
    private static IDisplayManager displayManager;
    private static IInputManager inputManager;
    private static IPermissionManager permissionManager;
    private static IPackageManager packageManager;
    private static IAudioService audioManager;

    public static void invalidate() {
        activityManager = null;
        activityTaskManager = null;
        windowManager = null;
        displayManager = null;
        inputManager = null;
        permissionManager = null;
        packageManager = null;
        audioManager = null;
    }

    private static void initWithShizuku() {
        if (!ShizukuUtils.hasPermission()) {
            return;
        }
        activityTaskManager = IActivityTaskManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity_task")));
        activityManager = IActivityManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)));
        windowManager = IWindowManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.WINDOW_SERVICE)));
        displayManager = IDisplayManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE)));
        inputManager = IInputManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.INPUT_SERVICE)));
        try {
            permissionManager = IPermissionManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("permissionmgr")));
        } catch(Throwable e) {
            // ignore;
        }
        packageManager = IPackageManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")));
        audioManager = IAudioService.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.AUDIO_SERVICE)));
    }

    /**
     * Check whether a given service is currently running.
     *
     * @param mContext    application context
     * @param serviceName fully qualified service class name (e.g. net.loonggg.testbackstage.TestService)
     * @return true if the service is running, false otherwise
     */
    public static boolean isServiceWork(Context mContext, String serviceName) {
        boolean isWork = false;
        ActivityManager myAM = (ActivityManager) mContext
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> myList = myAM.getRunningServices(40);
        if (myList.isEmpty()) {
            return false;
        }
        for (int i = 0; i < myList.size(); i++) {
            String mName = myList.get(i).service.getClassName();
            if (mName.equals(serviceName)) {
                isWork = true;
                break;
            }
        }
        return isWork;
    }

    public static int startActivity(Intent intent, ActivityOptions options) {
        if (activityManager == null) {
            initWithShizuku();
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                return activityManager.startActivityAsUserWithFeature(
                        null, "com.android.shell", null, intent,
                        intent.getType(), null, null, 0, 0,
                        null, options.toBundle(), 0
                );
            } else {
                return activityManager.startActivity(
                        null, "com.android.shell", intent,
                        intent.getType(), null, null, 0, 0,
                        null, options.toBundle()
                );
            }
        } catch (Exception e) {
            State.log("failed to start activity: " + e.getMessage());
            return -1;
        }
    }

    public static void launchPackage(Context context, String packageName, int targetDisplayId) {
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(targetDisplayId);
        if (display == null) {
            return;
        }
        _launchPackage(context, packageName, targetDisplayId);
        if (android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            _launchPackage(context, packageName, targetDisplayId);
        }
        if (targetDisplayId != Display.DEFAULT_DISPLAY) {
            State.lastSingleAppDisplay = targetDisplayId;
        }
    }
    private static void _launchPackage(Context context, String packageName, int targetDisplayId) {
        if (ShizukuUtils.hasPermission()) {
            launchAppWithShizuku(packageName, context, targetDisplayId);
            return;
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launchAppNormally(packageName, context, targetDisplayId);
            } else {
                if (State.getDisplaylinkVirtualDisplayId() == targetDisplayId) {
                    launchAppWithShizuku(packageName, context, targetDisplayId);
                } else {
                    launchAppNormally(packageName, context, targetDisplayId);
                }
            }
        } catch (Exception e) {
            if (ShizukuUtils.hasPermission()) {
                launchAppWithShizuku(packageName, context, targetDisplayId);
            } else {
                Toast.makeText(context, "Failed to launch app, this display requires Shizuku authorization", Toast.LENGTH_SHORT).show();
                State.log("Failed to launch app, this display requires Shizuku authorization: " + e);
            }
        }
    }

    private static void launchAppNormally(String packageName, Context context, int targetDisplayId) {
        PackageManager packageManager = context.getPackageManager();
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(targetDisplayId);
            context.startActivity(launchIntent, options.toBundle());
            State.startNewJob(new BindAllExternalInputToDisplay(targetDisplayId));
        }
    }

    private static void launchAppWithShizuku(String packageName, Context context, int targetDisplayId) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PackageManager packageManager = context.getPackageManager();
            ComponentName componentName = packageManager.getLaunchIntentForPackage(packageName).getComponent();
            intent.setComponent(componentName);
            intent.setPackage(packageName);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(targetDisplayId);
            ActivityOptionsHidden optionsHidden = Refine.unsafeCast(options);
            optionsHidden.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
            int result = ServiceUtils.startActivity(intent, options);
            if (result < 0) {
                Toast.makeText(context, "Failed to launch app via Shizuku", Toast.LENGTH_SHORT).show();
                State.log("Failed to launch app via Shizuku, return value: " + result);
            } else {
                State.log("Successfully launched app via Shizuku: " + packageName);
            }
            State.startNewJob(new BindAllExternalInputToDisplay(targetDisplayId));
        } catch (Exception e) {
            Toast.makeText(context, "Failed to launch app via Shizuku", Toast.LENGTH_SHORT).show();
            State.log("Failed to launch app via Shizuku: " + e.getMessage());
        }
    }

    public static int callPendingIntent(PendingIntent pendingIntent, ActivityOptions options, int displayId) {
        if (activityManager == null) {
            throw new IllegalStateException("ServiceUtils not initialized, call initWithShizuku() first");
        }

        try {
            PendingIntentHidden pendingIntentHidden = Refine.unsafeCast(pendingIntent);
            ActivityOptionsHidden optionsHidden = Refine.unsafeCast(options);
            optionsHidden.setCallerDisplayId(displayId);

            return activityManager.sendIntentSender(
                    pendingIntentHidden.getTarget(), pendingIntentHidden.getWhitelistToken(), 0, null,
                    null, null, null, optionsHidden.toBundle()
            );
        } catch (Exception e) {
            State.log("failed to send pending intent: " + e.getMessage());
            return -1;
        }
    }

    public static IWindowManager getWindowManager() {
        if (windowManager == null) {
            initWithShizuku();
        }
        return windowManager;
    }

    public static IDisplayManager getDisplayManager() {
        if (displayManager == null) {
            initWithShizuku();
        }
        return displayManager;
    }

    public static IInputManager getInputManager() {
        if (inputManager == null) {
            initWithShizuku();
        }
        return inputManager;
    }

    public static IActivityTaskManager getActivityTaskManager() {
        if (activityTaskManager == null) {
            initWithShizuku();
        }
        return activityTaskManager;
    }

    public static IPermissionManager getPermissionManager() {
        if (permissionManager == null) {
            initWithShizuku();
        }
        return permissionManager;
    }

    public static IPackageManager getPackageManager() {
        if (packageManager == null) {
            initWithShizuku();
        }
        return packageManager;
    }

    public static IAudioService getAudioManager() {
        if (audioManager == null) {
            initWithShizuku();
        }
        return audioManager;
    }

    public static void launchActivity(Context context, Class<?> activityClass, int targetDisplayId) {
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(targetDisplayId);
        if (display == null) {
            return;
        }
        _launchActivity(context, activityClass, targetDisplayId);
        if (android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            _launchActivity(context, activityClass, targetDisplayId);
        }
        if (targetDisplayId != Display.DEFAULT_DISPLAY) {
            State.lastSingleAppDisplay = targetDisplayId;
        }
    }

    private static void _launchActivity(Context context, Class<?> activityClass, int targetDisplayId) {
        if (ShizukuUtils.hasPermission()) {
            launchActivityWithShizuku(context, activityClass, targetDisplayId);
            return;
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launchActivityNormally(context, activityClass, targetDisplayId);
            } else {
                if (State.getDisplaylinkVirtualDisplayId() == targetDisplayId) {
                    launchActivityWithShizuku(context, activityClass, targetDisplayId);
                } else {
                    launchActivityNormally(context, activityClass, targetDisplayId);
                }
            }
        } catch (Exception e) {
            launchActivityWithShizuku(context, activityClass, targetDisplayId);
        }
    }

    private static void launchActivityNormally(Context context, Class<?> activityClass, int targetDisplayId) {
        Intent launchIntent = new Intent(context, activityClass);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(targetDisplayId);
        context.startActivity(launchIntent, options.toBundle());
    }

    private static void launchActivityWithShizuku(Context context, Class<?> activityClass, int targetDisplayId) {
        try {
            Intent intent = new Intent(context, activityClass);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(targetDisplayId);
            ActivityOptionsHidden optionsHidden = Refine.unsafeCast(options);
            optionsHidden.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
            int result = ServiceUtils.startActivity(intent, options);
            if (result < 0) {
                Toast.makeText(context, "Failed to start activity via Shizuku", Toast.LENGTH_SHORT).show();
                State.log("Failed to start activity via Shizuku, return value: " + result);
            } else {
                State.log("Successfully started activity via Shizuku: " + activityClass.getName());
            }
        } catch (Exception e) {
            Toast.makeText(context, "Failed to start activity via Shizuku", Toast.LENGTH_SHORT).show();
            State.log("Failed to start activity via Shizuku: " + e.getMessage());
        }
    }
}