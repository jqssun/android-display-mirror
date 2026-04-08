package io.github.jqssun.displaymirror;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEventHidden;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import io.github.jqssun.displaymirror.job.CreateVirtualDisplay;
import io.github.jqssun.displaymirror.job.ExitAll;
import io.github.jqssun.displaymirror.job.InputRouting;
import io.github.jqssun.displaymirror.job.VirtualDisplayArgs;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import dev.rikka.tools.refine.Refine;

public class BridgeActivity extends AppCompatActivity {

    private static BridgeActivity instance;

    public static BridgeActivity getInstance() {
        return instance;
    }

    private SurfaceView surfaceView;

    private static float[] _adjustTouchCoordinates(float x, float y, int rotation,
                                                  int targetWidth, int targetHeight, int sourceWidth, int sourceHeight) {
        float scaleX = (float) targetWidth / sourceWidth;
        float scaleY = (float) targetHeight / sourceHeight;

        x *= scaleX;
        y *= scaleY;

        float[] result = new float[2];
        switch (rotation) {
            case Surface.ROTATION_0:
                result[0] = x;
                result[1] = y;
                break;
            case Surface.ROTATION_90:
                result[0] = y;
                result[1] = targetWidth - x;
                break;
            case Surface.ROTATION_180:
                result[0] = targetWidth - x;
                result[1] = targetHeight - y;
                break;
            case Surface.ROTATION_270:
                result[0] = targetHeight - y;
                result[1] = x;
                break;
        }
        return result;
    }

    public static void stopVirtualDisplay() {
        State.stopMirrorVirtualDisplay();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            window.setAttributes(layoutParams);
        }

        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        surfaceView = new SurfaceView(this);
        VirtualDisplayArgs args = getIntent().getParcelableExtra("virtualDisplayArgs");
        
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Surface surface = holder.getSurface();
                
                if (State.mirrorVirtualDisplay == null) {
                    stopVirtualDisplay();
                    State.mirrorVirtualDisplay = CreateVirtualDisplay.createVirtualDisplay(args, surface);
                } else {
                    State.mirrorVirtualDisplay.setSurface(surface);
                }
                int mirrorDisplayId = State.mirrorVirtualDisplay.getDisplay().getDisplayId();
                String selectedAppPackage = Pref.getSelectedAppPackage();
                ServiceUtils.launchPackage(BridgeActivity.this, selectedAppPackage, mirrorDisplayId);
                if (ShizukuUtils.hasPermission()) {
                    InputRouting.bindAllExternalInputToDisplay(mirrorDisplayId);
                }
                InputRouting.moveImeToExternal(mirrorDisplayId);
                DisplayManager displayManager2 = (DisplayManager) BridgeActivity.this
                        .getSystemService(Context.DISPLAY_SERVICE);
                displayManager2.registerDisplayListener(new DisplayManager.DisplayListener() {
                    @Override
                    public void onDisplayAdded(int i) {

                    }

                    @Override
                    public void onDisplayRemoved(int i) {
                        if (i == mirrorDisplayId) {
                            CreateVirtualDisplay.powerOnScreen();
                            ExitAll.execute(State.getContext(), false);
                        }
                    }

                    @Override
                    public void onDisplayChanged(int i) {

                    }
                }, null);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (State.mirrorVirtualDisplay != null) {
                    ImageReader imageReader = ImageReader.newInstance(args.width, args.height, 1, 2);
                    State.mirrorVirtualDisplay.setSurface(imageReader.getSurface());
                }
            }
        });
        
        surfaceView.setOnTouchListener((v, event) -> {
            if (State.mirrorVirtualDisplay != null) {
                Display virtualDisplay = State.mirrorVirtualDisplay.getDisplay();
                int rotation = virtualDisplay.getRotation();
                int displayId = virtualDisplay.getDisplayId();

                float x = event.getX();
                float y = event.getY();
                
                float[] adjustedCoords = _adjustTouchCoordinates(x, y, rotation, 
                    args.width, args.height,
                    surfaceView.getWidth(), surfaceView.getHeight());
                
                event.setLocation(adjustedCoords[0], adjustedCoords[1]);
                
                MotionEventHidden motionEventHidden = Refine.unsafeCast(event);
                motionEventHidden.setDisplayId(displayId);
                try {
                    ServiceUtils.getInputManager().injectInputEvent(event, 0);
                } catch (Exception e) {
                    Log.e("BridgeActivity", "Failed to inject touch event", e);
                }
            }
            return true;
        });
        
        setContentView(surfaceView);
    }

    @Override
    protected void onDestroy() {
        Log.i("BridgeActivity", "BridgeActivity onDestroy");
        super.onDestroy();
        Intent intent = new Intent(this, MirrorMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(0);
        this.startActivity(intent, options.toBundle());
        instance = null;
    }
}