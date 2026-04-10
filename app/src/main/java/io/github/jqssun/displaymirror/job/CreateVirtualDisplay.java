package io.github.jqssun.displaymirror.job;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IDisplayManager;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.media.projection.IMediaProjection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionHidden;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.IWindowManager;
import android.view.Surface;

import androidx.annotation.NonNull;

import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.PureBlackActivity;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;
import io.github.jqssun.displaymirror.shizuku.SurfaceControl;

import java.lang.reflect.Constructor;

import dev.rikka.tools.refine.Refine;

public class CreateVirtualDisplay {

    // Internal fields copied from android.hardware.display.DisplayManager
    private static final int VIRTUAL_DISPLAY_FLAG_PUBLIC = android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY = android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT = 1 << 7;
    private static final int VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 9;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED = 1 << 12;
    private static final int VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED = 1 << 13;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_FOCUS = 1 << 14;
    private static final int VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP = 1 << 15;
    public static boolean isCreating = false;

    public static VirtualDisplay createVirtualDisplay(VirtualDisplayArgs virtualDisplayArgs, Surface surface) {
        isCreating = true;
        try {
            if (ShizukuUtils.hasPermission()) {
                try {
                    VirtualDisplay virtualDisplay = _createByShizuku(virtualDisplayArgs, surface, true, null);
                    android.util.Log.i("CreateVirtualDisplay", "created virtual display: " + virtualDisplay.getDisplay().getDisplayId());
                    powerOffScreen();
                    return virtualDisplay;
                } catch(Exception e) {
                    VirtualDisplay virtualDisplay = _createByShizuku(virtualDisplayArgs, surface, true, State.getMediaProjection());
                    android.util.Log.i("CreateVirtualDisplay", "created virtual display: " + virtualDisplay.getDisplay().getDisplayId());
                    powerOffScreen();
                    return virtualDisplay;
                }
            } else {
                new Handler(Looper.getMainLooper()).post(() -> {
                   State.log("Cannot use single-app projection without Shizuku permission");
                });
                return null;
            }
        } finally {
            isCreating = false;
        }
    }

    public static void powerOffScreen() {
        Context context = State.getContext();
        if (context == null) {
            return;
        }
        boolean autoScreenOff = Pref.getAutoScreenOff();
        if (!autoScreenOff) {
            return;
        }
        doPowerOffScreen(context);
    }

    public static void doPowerOffScreen(Context context) {
        boolean singleApp = Pref.getSingleAppMode();
        if (State.userService != null && !Pref.getUseBlackImage()) {
            try {
                State.userService.startListenVolumeKey();
                if (!State.userService.setScreenPower(SurfaceControl.POWER_MODE_OFF)) {
                    if (singleApp) {
                        Intent intent = new Intent(context, PureBlackActivity.class);
                        ActivityOptions options = ActivityOptions.makeBasic();
                        context.startActivity(intent, options.toBundle());
                    }
                }
            } catch (RemoteException e2) {
                State.log("powerOffScreen failed: " + e2.getMessage());
            }
        } else if (singleApp) {
            Intent intent = new Intent(context, PureBlackActivity.class);
            ActivityOptions options = ActivityOptions.makeBasic();
            context.startActivity(intent, options.toBundle());
        } else {
            State.log("Shizuku permission required for screen-off during mirror projection");
        }
    }

    private static VirtualDisplay _createByMediaProjection(VirtualDisplayArgs virtualDisplayArgs, Surface surface) {
        VirtualDisplay virtualDisplay = State.getMediaProjection().createVirtualDisplay(virtualDisplayArgs.virtualDisplayName,
                virtualDisplayArgs.width, virtualDisplayArgs.height, virtualDisplayArgs.dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                surface, null, null);
        State.setMediaProjection(null);
        return virtualDisplay;
    }

    private static @NonNull VirtualDisplay _createByShizuku(VirtualDisplayArgs virtualDisplayArgs, Surface surface, boolean ownContentOnly, MediaProjection mediaProjection) {
        int virtualDisplayWidth = virtualDisplayArgs.width;
        IDisplayManager displayManager = ServiceUtils.getDisplayManager();
        int flags = getFlags(ownContentOnly, virtualDisplayArgs.rotatesWithContent);
        VirtualDisplayConfig config = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            config = new VirtualDisplayConfig.Builder(
                    virtualDisplayArgs.virtualDisplayName,
                    virtualDisplayWidth, virtualDisplayArgs.height, virtualDisplayArgs.dpi)
                    .setSurface(surface)
                    .setFlags(flags)
                    .setRequestedRefreshRate(virtualDisplayArgs.refreshRate)
                    .build();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            config = new VirtualDisplayConfig.Builder(
                    virtualDisplayArgs.virtualDisplayName,
                    virtualDisplayWidth, virtualDisplayArgs.height, virtualDisplayArgs.dpi)
                    .setSurface(surface)
                    .setFlags(flags)
                    .build();
        } else {
            // config = null
        }
        IVirtualDisplayCallback callback = new VirtualDisplayCallback();
        IMediaProjection projection = null;
        if (mediaProjection != null) {
            MediaProjectionHidden mediaProjectionHidden = Refine.unsafeCast(mediaProjection);
            projection = mediaProjectionHidden.getProjection();
        }
        int displayId = -1;
        String packageName = "com.android.shell";
        try {
            if (State.userService != null && State.userService.isRooted()) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    State.log("Shizuku started with root may not support single-app projection, consider restarting with adb instead");
                });
            }
        } catch (Throwable e) {
            // ignore;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayId = displayManager.createVirtualDisplay(config, callback, projection, packageName);
        } else {
            displayId = displayManager.createVirtualDisplay(callback, projection, packageName, virtualDisplayArgs.virtualDisplayName, virtualDisplayWidth, virtualDisplayArgs.height, virtualDisplayArgs.dpi, surface, flags, virtualDisplayArgs.virtualDisplayName);
        }
        DisplayInfo displayInfo = ServiceUtils.getDisplayManager().getDisplayInfo(displayId);
        android.util.Log.i("CreateVirtualDisplay", "Virtual display created, displayId: " + displayId + ", uniqueId: " + displayInfo.uniqueId);
        VirtualDisplay virtualDisplay = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            virtualDisplay = DisplayManagerGlobal.getInstance().createVirtualDisplayWrapper(config, callback, displayId);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            virtualDisplay = DisplayManagerGlobal.getInstance().createVirtualDisplayWrapper(config, null, callback, displayId);
        } else {
            try {
                DisplayManagerGlobal displayManagerGlobal = DisplayManagerGlobal.getInstance();
                Class<?> virtualDisplayClass = VirtualDisplay.class;
                Constructor<?> constructor = virtualDisplayClass.getDeclaredConstructor(
                        DisplayManagerGlobal.class,
                        Display.class,
                        IVirtualDisplayCallback.class,
                        Surface.class
                );
                constructor.setAccessible(true);
                Display display = displayManagerGlobal.getRealDisplay(displayId);
                virtualDisplay = (VirtualDisplay) constructor.newInstance(
                        displayManagerGlobal,
                        display,
                        callback,
                        surface
                );
            } catch(Throwable e) {
                throw new RuntimeException(e);
            }
        }
        State.setMediaProjection(null);
        return virtualDisplay;
    }

    public static int getFlags(boolean ownContentOnly, boolean rotatesWithContent) {
        int flags = VIRTUAL_DISPLAY_FLAG_PUBLIC
                | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH;
        //    | VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL;
        if (ownContentOnly) {
            flags |= VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
        }
        if (rotatesWithContent) {
            flags |= VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT;
        }
        if (Build.VERSION.SDK_INT >= AndroidVersions.API_33_ANDROID_13) {
            flags |= VIRTUAL_DISPLAY_FLAG_TRUSTED
                    | VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP
                    | VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                    | VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED;
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14) {
                flags |= VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP;
                //    flags |= VIRTUAL_DISPLAY_FLAG_OWN_FOCUS
                //            | VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP;
            }
        }
        return flags;
    }

    public static void powerOnScreen() {
        if (State.isInPureBlackActivity != null) {
            State.isInPureBlackActivity.finish();
        } else {
            if (State.userService != null) {
                try {
                    State.userService.stopListenVolumeKey();
                    State.userService.setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
                } catch (RemoteException e) {
                    State.log("powerUpScreen failed: " + e.getMessage());
                }
            }
        }
    }

    private static boolean _shouldChangeAspectRatio() {
        return ShizukuUtils.hasPermission() && Pref.getAutoMatchAspectRatio();
    }

    public static void changeAspectRatio(int width, int height) {
        if(!_shouldChangeAspectRatio()) {
            return;
        }
        IWindowManager wm = ServiceUtils.getWindowManager();
        Point baseSize = new Point();
        wm.getInitialDisplaySize(Display.DEFAULT_DISPLAY, baseSize);
        int internalWidth = Math.min(baseSize.x, baseSize.y);
        int internalHeight = Math.max(baseSize.x, baseSize.y);
        float externalWidth = Math.min(width, height);
        float externalHeight = Math.max(width, height);
        internalHeight = (int) (internalWidth * (externalHeight / externalWidth));
        if (internalHeight < 1600) {
            return;
        }
        ServiceUtils.getWindowManager().setForcedDisplaySize(Display.DEFAULT_DISPLAY, internalWidth, internalHeight);
    }

    public static void restoreAspectRatio() {
        if(!ShizukuUtils.hasPermission()) {
            return;
        }
        if(!_shouldChangeAspectRatio()) {
            return;
        }
        try {
            IWindowManager wm = ServiceUtils.getWindowManager();
            Point baseSize = new Point();
            wm.getBaseDisplaySize(Display.DEFAULT_DISPLAY, baseSize);
            Point initialSize = new Point();
            wm.getInitialDisplaySize(Display.DEFAULT_DISPLAY, initialSize);
            if (baseSize.y == initialSize.y) {
                return;
            }
            wm.clearForcedDisplaySize(Display.DEFAULT_DISPLAY);
            wm.setForcedDisplaySize(Display.DEFAULT_DISPLAY, initialSize.x, initialSize.y);
        } catch (Exception ignored) {
        }
    }

    public static class VirtualDisplayCallback extends IVirtualDisplayCallback.Stub {
        public void onPaused() {
        }
        public void onResumed() {
        }
        public void onStopped() {
        }
    }
}
