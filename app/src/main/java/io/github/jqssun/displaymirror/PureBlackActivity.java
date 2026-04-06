package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEventHidden;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.content.BroadcastReceiver;

import androidx.appcompat.app.AppCompatActivity;

import io.github.jqssun.displaymirror.job.CreateVirtualDisplay;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;
import io.github.jqssun.displaymirror.shizuku.SurfaceControl;

import dev.rikka.tools.refine.Refine;

import java.util.HashSet;
import java.util.Set;

public class PureBlackActivity extends AppCompatActivity {
    private final Set<Integer> externalDeviceIds = new HashSet<>();
    private final boolean hasShizukuPermission = ShizukuUtils.hasPermission();
    private IInputManager inputManager;

    public static class ExitReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            State.log("Waking up from screen off");
            CreateVirtualDisplay.powerOnScreen();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        State.isInPureBlackActivity = this;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }

        // support display cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            window.setAttributes(layoutParams);
        }

        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        View view = new View(this);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setBackgroundColor(Color.BLACK);
        setContentView(view);

        view.setOnGenericMotionListener((v, event) -> {
            TouchpadActivity.setFocus(inputManager, Display.DEFAULT_DISPLAY);
            view.requestFocus();
            view.requestFocusFromTouch();
            view.requestPointerCapture();
            return false;
        });
        
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);

        view.setOnTouchListener((v, event) -> {
            if (isExternalDevice(event)) {
                Display targetDisplay = displayManager.getDisplay(State.lastSingleAppDisplay);
                if (targetDisplay == null)
                    return true;

                float x = event.getX();
                float y = event.getY();

                float relativeX = x / v.getWidth();
                float relativeY = y / v.getHeight();

                int rotation = targetDisplay.getRotation();
                float targetWidth = targetDisplay.getWidth();
                float targetHeight = targetDisplay.getHeight();
                
                float mappedX, mappedY;
                switch (rotation) {
                    case Surface.ROTATION_270:
                        mappedX = (1 - relativeY) * targetWidth;
                        mappedY = relativeX * targetHeight;
                        break;
                    case Surface.ROTATION_180:
                        mappedX = (1 - relativeX) * targetWidth;
                        mappedY = (1 - relativeY) * targetHeight;
                        break;
                    case Surface.ROTATION_90:
                        mappedX = relativeY * targetWidth;
                        mappedY = (1 - relativeX) * targetHeight;
                        break;
                    default: // Surface.ROTATION_0
                        mappedX = relativeX * targetWidth;
                        mappedY = relativeY * targetHeight;
                        break;
                }
                event.setLocation(mappedX, mappedY);

                MotionEventHidden motionEventHidden = Refine.unsafeCast(event);
                motionEventHidden.setDisplayId(State.lastSingleAppDisplay);
                ServiceUtils.getInputManager().injectInputEvent(event, 0);
                return true;
            }
            finish();
            return true;
        });
       if (ShizukuUtils.hasPermission()) {
           inputManager = ServiceUtils.getInputManager();
           TouchpadActivity.setFocus(inputManager, State.lastSingleAppDisplay);
           TouchpadAccessibilityService.startServiceByShizuku(this);
           new Handler().postDelayed(() -> {
               TouchpadActivity.setFocus(inputManager, State.lastSingleAppDisplay);
           }, 500);
       } else if(TouchpadAccessibilityService.getInstance() != null) {
           TouchpadActivity.setFocus(null, State.lastSingleAppDisplay);
           new Handler().postDelayed(() -> {
               TouchpadActivity.setFocus(null, State.lastSingleAppDisplay);
           }, 500);
       } else if (TouchpadAccessibilityService.isAccessibilityServiceEnabled(this)) {
           Intent serviceIntent = new Intent(this, TouchpadAccessibilityService.class);
           this.startService(serviceIntent);
           new Handler().postDelayed(() -> {
               TouchpadActivity.setFocus(null, State.lastSingleAppDisplay);
           }, 500);
       }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        State.isInPureBlackActivity = null;
    }

    private boolean isExternalDevice(MotionEvent event) {
        if (!hasShizukuPermission) {
            return false;
        }
        int deviceId = event.getDeviceId();
        if (externalDeviceIds.contains(deviceId)) {
            return true;
        }
        InputDevice device = InputDevice.getDevice(deviceId);
        if (device != null) {
            if (device.isExternal()) {
                externalDeviceIds.add(deviceId);
                return true;
            }
        }
        return false;
    }
}