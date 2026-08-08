package io.github.jqssun.displaymirror.job;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
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
import dev.rikka.tools.refine.Refine;
import io.github.jqssun.displaymirror.CastPlaceholderActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;
import io.github.jqssun.displaymirror.shizuku.SurfaceControl;
import java.lang.reflect.Constructor;

public class CreateVirtualDisplay {

  public static VirtualDisplay createVirtualDisplay(
      VirtualDisplayArgs virtualDisplayArgs, Surface surface) {
    if (ShizukuUtils.hasPermission()) {
      VirtualDisplay virtualDisplay = _createByShizukuWithFallback(virtualDisplayArgs, surface);
      android.util.Log.i(
          "CreateVirtualDisplay",
          "created virtual display: " + virtualDisplay.getDisplay().getDisplayId());
      State.setMediaProjection(null);
      if (!Pref.getMirrorOnly()) {
        _showCastPlaceholder(virtualDisplay.getDisplay().getDisplayId());
      }
      return virtualDisplay;
    } else {
      new Handler(Looper.getMainLooper())
          .post(
              () -> {
                State.log("cannot use single-app projection without Shizuku permission");
              });
      return null;
    }
  }

  public static boolean streamMirrors() {
    return Pref.getMirrorOnly() || !Pref.getTrustedDisplay() || !ShizukuUtils.hasPermission();
  }

  public static VirtualDisplay createForStream(VirtualDisplayArgs args, Surface surface) {
    if (ShizukuUtils.hasPermission() && Pref.getTrustedDisplay()) {
      // decide before createVirtualDisplay nulls State's projection slot
      boolean mirror = streamMirrors() && State.getMediaProjection() != null;
      try {
        VirtualDisplay display = createVirtualDisplay(args, surface);
        if (mirror) {
          changeAspectRatio(args.width, args.height);
        }
        return display;
      } catch (Throwable e) {
        State.log("trusted display creation failed, falling back to untrusted: " + e.getMessage());
      }
    }
    MediaProjection projection = State.getMediaProjection();
    if (projection == null) {
      return null;
    }
    VirtualDisplay display =
        projection.createVirtualDisplay(
            args.virtualDisplayName,
            args.width,
            args.height,
            args.dpi,
            DisplayFlags.PUBLIC,
            surface,
            null,
            null);
    State.setMediaProjection(null);
    changeAspectRatio(args.width, args.height);
    return display;
  }

  // an own-content display is black until an app is launched; show a placeholder so the user
  // knows to pick one in Extend. delayed so the display is registered in WM before we launch.
  private static void _showCastPlaceholder(int displayId) {
    Context context = State.getContext();
    if (context == null) {
      return;
    }
    new Handler(Looper.getMainLooper())
        .postDelayed(() -> CastPlaceholderActivity.launchOnDisplay(context, displayId), 300);
  }

  // custom flags the service rejects get logged, then retried with the automatic set
  private static @NonNull VirtualDisplay _createByShizukuWithFallback(
      VirtualDisplayArgs virtualDisplayArgs, Surface surface) {
    int structural = _structuralFlags(true, virtualDisplayArgs.rotatesWithContent);
    int flags = structural | DisplayFlags.current();
    int autoFlags = structural | DisplayFlags.auto();
    try {
      return _createByShizuku(virtualDisplayArgs, surface, flags);
    } catch (RuntimeException e) {
      if (flags == autoFlags) {
        throw e;
      }
      State.log("custom display flags rejected (" + e.getMessage() + "), retrying with automatic");
      return _createByShizuku(virtualDisplayArgs, surface, autoFlags);
    }
  }

  private static @NonNull VirtualDisplay _createByShizuku(
      VirtualDisplayArgs virtualDisplayArgs, Surface surface, int flags) {
    int virtualDisplayWidth = virtualDisplayArgs.width;
    IDisplayManager displayManager = ServiceUtils.getDisplayManager();
    VirtualDisplayConfig config = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      config =
          buildVirtualDisplayConfig(virtualDisplayArgs, surface, flags, virtualDisplayWidth, true);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      config =
          buildVirtualDisplayConfig(virtualDisplayArgs, surface, flags, virtualDisplayWidth, false);
    } else {
      // config = null
    }
    IVirtualDisplayCallback callback = new VirtualDisplayCallback();
    // binding a MediaProjection makes the display mirror-only (shouldOnlyMirror) and unable to
    // host launched apps on android 17; only do it when the user opts into projection-backed
    // mirroring, otherwise keep it null so shell owns a trusted own-content display
    IMediaProjection projection = null;
    MediaProjection mediaProjection = State.getMediaProjection();
    if (Pref.getMirrorOnly() && mediaProjection != null) {
      MediaProjectionHidden hidden = Refine.unsafeCast(mediaProjection);
      projection = hidden.getProjection();
    }
    int displayId = -1;
    String packageName = "com.android.shell";
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      displayId = displayManager.createVirtualDisplay(config, callback, projection, packageName);
    } else {
      displayId =
          displayManager.createVirtualDisplay(
              callback,
              projection,
              packageName,
              virtualDisplayArgs.virtualDisplayName,
              virtualDisplayWidth,
              virtualDisplayArgs.height,
              virtualDisplayArgs.dpi,
              surface,
              flags,
              virtualDisplayArgs.virtualDisplayName);
    }
    DisplayInfo displayInfo = ServiceUtils.getDisplayManager().getDisplayInfo(displayId);
    android.util.Log.i(
        "CreateVirtualDisplay",
        "Virtual display created, displayId: " + displayId + ", uniqueId: " + displayInfo.uniqueId);
    VirtualDisplay virtualDisplay = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      virtualDisplay =
          DisplayManagerGlobal.getInstance()
              .createVirtualDisplayWrapper(config, callback, displayId);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      virtualDisplay =
          DisplayManagerGlobal.getInstance()
              .createVirtualDisplayWrapper(config, null, callback, displayId);
    } else {
      try {
        DisplayManagerGlobal displayManagerGlobal = DisplayManagerGlobal.getInstance();
        Class<?> virtualDisplayClass = VirtualDisplay.class;
        Constructor<?> constructor =
            virtualDisplayClass.getDeclaredConstructor(
                DisplayManagerGlobal.class,
                Display.class,
                IVirtualDisplayCallback.class,
                Surface.class);
        constructor.setAccessible(true);
        Display display = displayManagerGlobal.getRealDisplay(displayId);
        virtualDisplay =
            (VirtualDisplay)
                constructor.newInstance(displayManagerGlobal, display, callback, surface);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }
    State.setMediaProjection(null);
    return virtualDisplay;
  }

  @SuppressLint("NewApi")
  private static VirtualDisplayConfig buildVirtualDisplayConfig(
      VirtualDisplayArgs virtualDisplayArgs,
      Surface surface,
      int flags,
      int virtualDisplayWidth,
      boolean includeRefreshRate) {
    VirtualDisplayConfig.Builder builder =
        new VirtualDisplayConfig.Builder(
                virtualDisplayArgs.virtualDisplayName,
                virtualDisplayWidth,
                virtualDisplayArgs.height,
                virtualDisplayArgs.dpi)
            .setSurface(surface)
            .setFlags(flags);
    if (includeRefreshRate) {
      builder.setRequestedRefreshRate(virtualDisplayArgs.refreshRate);
    }
    return builder.build();
  }

  // owned by other settings, always applied regardless of custom flag set
  private static int _structuralFlags(boolean ownContentOnly, boolean rotatesWithContent) {
    int flags = 0;
    if (ownContentOnly) {
      flags |= DisplayFlags.OWN_CONTENT_ONLY;
    }
    if (rotatesWithContent) {
      flags |= DisplayFlags.ROTATES_WITH_CONTENT;
    }
    return flags;
  }

  public static void powerOnScreen() {
    if (State.userService != null) {
      try {
        State.userService.stopListenVolumeKey();
        State.userService.setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
      } catch (RemoteException e) {
        State.log("powerUpScreen failed: " + e.getMessage());
      }
    }
  }

  public static void changeAspectRatio(int width, int height) {
    if (!ShizukuUtils.hasPermission() || !Pref.getAutoMatchAspectRatio()) {
      return;
    }
    float aspect = (float) Math.max(width, height) / Math.min(width, height);
    IWindowManager wm = ServiceUtils.getWindowManager();
    Point baseSize = new Point();
    wm.getInitialDisplaySize(Display.DEFAULT_DISPLAY, baseSize);
    int internalWidth = Math.min(baseSize.x, baseSize.y);
    wm.setForcedDisplaySize(Display.DEFAULT_DISPLAY, internalWidth, (int) (internalWidth * aspect));
    Pref.setAspectForced(true);
  }

  public static void restoreAspectRatio() {
    if (!Pref.getAspectForced() || !ShizukuUtils.hasPermission()) {
      return;
    }
    try {
      ServiceUtils.getWindowManager().clearForcedDisplaySize(Display.DEFAULT_DISPLAY);
      Pref.setAspectForced(false);
    } catch (Exception ignored) {
    }
  }

  public static class VirtualDisplayCallback extends IVirtualDisplayCallback.Stub {
    @Override
    public void onPaused() {}

    @Override
    public void onResumed() {}

    @Override
    public void onStopped() {}
  }
}
