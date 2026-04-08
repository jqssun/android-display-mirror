package io.github.jqssun.displaymirror.job;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Surface;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.jqssun.displaymirror.MoonlightCursorOverlay;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.R;
import io.github.jqssun.displaymirror.State;

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
            
            com.google.android.material.textfield.TextInputLayout inputLayout = new com.google.android.material.textfield.TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
            com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(inputLayout.getContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setText(pinCandidate);
            input.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(4) });
            inputLayout.addView(input);
            int pad = (int)(16 * context.getResources().getDisplayMetrics().density);
            FrameLayout container = new FrameLayout(context);
            container.setPadding(pad, 0, pad, 0);
            container.addView(inputLayout);

            if (suppressPin != null) {
                submitPin(suppressPin);
            } else {
                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.enter_pin_title)
                        .setMessage(context.getString(R.string.enter_pin_message))
                        .setView(container)
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
            MoonlightCursorOverlay.hide();
            CreateVirtualDisplay.powerOnScreen();
            CreateVirtualDisplay.restoreAspectRatio();
            if (SunshineMouse.autoRotateAndScaleForMoonlight != null) {
                SunshineMouse.autoRotateAndScaleForMoonlight.stop();
                SunshineMouse.autoRotateAndScaleForMoonlight = null;
            }
            State.stopMirrorVirtualDisplay();
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
            
            new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.encoder_error_title)
                .setMessage(errorMessage)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    stopVirtualDisplay();
                })
                .setCancelable(false)
                .show();
        });
    }

    public static void onMirrorClientDiscovered(String mirrorClient) {
        if (State.discoveredMirrorClients.contains(mirrorClient)) {
            return;
        }
        State.discoveredMirrorClients.add(mirrorClient);
    }

    public static void setMirrorServerUuid(String uuid) {
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
