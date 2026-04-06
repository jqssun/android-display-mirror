package io.github.jqssun.displaymirror.job;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.os.Handler;
import android.os.Looper;
import android.app.AlertDialog;
import android.content.Context;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.IWindowManager;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEventHidden;
import android.view.Surface;
import android.widget.EditText;
import android.widget.Toast;
import android.media.AudioRecord;
import android.media.AudioManager;

import androidx.annotation.NonNull;

import io.github.jqssun.displaymirror.R;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.SunshineService;
import io.github.jqssun.displaymirror.TouchpadAccessibilityService;
import io.github.jqssun.displaymirror.TouchpadActivity;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.rikka.tools.refine.Refine;

// from Sunshine v2025.122.141614
public class SunshineServer {
    public static String suppressPin;
    public static String pinCandidate;

    static {
        System.loadLibrary("sunshine");
    }

    public static native void start();

    public static native void setSunshineName(String sunshineName);
    public static native void setPkeyPath(String path);
    public static native void setCertPath(String path);
    public static native void setFileStatePath(String path);
    
    public static void onPinRequested() {
        new Handler(Looper.getMainLooper()).post(() -> {
            Context context = State.getContext();
            if (context == null) {
                return;
            }
            
            final EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setText(pinCandidate);
            InputFilter[] filters = new InputFilter[1];
            filters[0] = new InputFilter.LengthFilter(4);
            input.setFilters(filters);
            
            if (suppressPin != null) {
                submitPin(suppressPin);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.enter_pin_title)
                        .setMessage(context.getString(R.string.enter_pin_message))
                        .setView(input)
                        .setPositiveButton(R.string.ok, (dialog, which) -> {
                            String pin = input.getText().toString();
                            if (pin.length() == 4) {
                                submitPin(pin);
                            } else {
                                Toast.makeText(context, R.string.enter_pin_message, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel())
                        .show();
            }
        });
    }
    
    public static native void submitPin(String pin);
    
    
    // surface created by MediaCodec
    // width always > height, as it is a landscape mode
    public static void createVirtualDisplay(int width, int height, int frameRate, int packetDuration, Surface surface, boolean shouldMute) {
        suppressPin = null;
        Context context = State.getContext();
        if (context == null) {
            return;
        }

        SunshineMouse.initialize(width, height);
        SunshineKeyboard.initialize();
        
        new Handler(Looper.getMainLooper()).post(() -> {
            State.startNewJob(new ProjectViaMoonlight(width, height, frameRate, packetDuration, surface, shouldMute));
        });
    }


    public static void stopVirtualDisplay() {
        new Handler(Looper.getMainLooper()).post(() -> {
            State.log("Stopping Moonlight projection");
            CreateVirtualDisplay.powerOnScreen();
            CreateVirtualDisplay.restoreAspectRatio();
            if (SunshineMouse.autoRotateAndScaleForMoonlight != null) {
                SunshineMouse.autoRotateAndScaleForMoonlight.stop();
                SunshineMouse.autoRotateAndScaleForMoonlight = null;
            }
            if (State.mirrorVirtualDisplay != null) {
                State.mirrorVirtualDisplay.release();
                State.mirrorVirtualDisplay = null;
            }
            Context context = State.getContext();
            ExitAll.execute(context, true);
        });
    }

    public static native void startAudioRecording(Object audioRecord, int framesPerPacket);

    public static native void enableH265();

    public static void showEncoderError(String errorMessage) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Context context = State.getContext();
            if (context == null) {
                return;
            }
            
            new AlertDialog.Builder(context)
                .setTitle(R.string.encoder_error_title)
                .setMessage(errorMessage)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    stopVirtualDisplay();
                })
                .setCancelable(false)
                .show();
        });
    }

    public static void onConnectScreenClientDiscovered(String connectScreenClient) {
        if (State.discoveredConnectScreenClients.contains(connectScreenClient)) {
            return;
        }
        State.discoveredConnectScreenClients.add(connectScreenClient);
    }

    public static void setConnectScreenServerUuid(String uuid) {
        State.serverUuid = uuid;
        if (!Pref.doNotAutoStartMoonlight) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (Pref.getAutoConnectClient() && !Pref.getSelectedClient().isEmpty()) {
                    ConnectToClient.connect((int)(Math.random() * 9000) + 1000);
                }
            }, 1000);
        }
    }

    public static native boolean exitServer();

}
