package io.github.jqssun.displaymirror.job;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.displaylink.manager.NativeDriver;
import com.displaylink.manager.NativeDriverListener;
import com.displaylink.manager.display.DisplayMode;

import io.github.jqssun.displaymirror.ApkImporter;
import io.github.jqssun.displaymirror.DisplaylinkState;
import io.github.jqssun.displaymirror.MirrorMainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.SunshineService;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProjectViaDisplaylink implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    private boolean usbRequested = false;
    private boolean device2UsbRequested = false;
    private boolean mediaProjectionRequested = false;
    private final String deviceName;
    private final VirtualDisplayArgs virtualDisplayArgs;

    public ProjectViaDisplaylink(UsbDevice device, VirtualDisplayArgs virtualDisplayArgs) {
        this.deviceName = device.getDeviceName();
        this.virtualDisplayArgs = virtualDisplayArgs;
    }

    public void start() throws YieldException {
        Context context = State.getContext();
        if (context == null) {
            State.log("Activity does not exist, skipping task");
            return;
        }
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        DisplaylinkState displaylinkState = State.displaylinkState;

        if (displaylinkState == null) {
            State.log("USB device " + deviceName + " state not found, skipping task");
            return;
        }

        if (displaylinkState.displaylinkDevice2 != null && displaylinkState.getVirtualDisplay() != null) {
            displaylinkState.destroy();
        }

        if (!ApkImporter.areLibsImported(context)) {
            State.showErrorStatus("DisplayLink libraries not imported. Please import the DisplayLink APK first.");
            return;
        }
        NativeDriver.load(ApkImporter.jniLibDir(context));
        _copyFirmwares(context);

        if (!_requestUsbPermission(context, usbManager, displaylinkState.device)) {
            return;
        }
        if (!_requestDevice2UsbPermission(context, usbManager, displaylinkState)) {
            return;
        }
        _openUsbConnection(context, usbManager, displaylinkState);
        if (!_initializeNativeDriver(context, displaylinkState)) {
            return;
        }
        boolean singleAppMode = Pref.getSingleAppMode();
        if (singleAppMode) {
            if (ShizukuUtils.hasPermission()) {
                String selectedAppPackage = Pref.getSelectedAppPackage();
                createVirtualDisplay(context, State.displaylinkState, selectedAppPackage);
            } else {
                State.showErrorStatus("DisplayLink single-app projection requires Shizuku permission");
            }
        } else {
            if (_requestMediaProjectionPermission(context, displaylinkState)) {
                displaylinkState.nativeDriver.setMode(displaylinkState.encoderId, new DisplayMode(virtualDisplayArgs.width, virtualDisplayArgs.height, virtualDisplayArgs.refreshRate), virtualDisplayArgs.width * 4, 1);
                new AutoRotateAndScaleForDisplaylink(virtualDisplayArgs, context);
            }
        }
    }


    private void createVirtualDisplay(Context context, DisplaylinkState displaylinkState, String lastPackageName) {
        int singleAppDpi = Pref.getSingleAppDpi();
        virtualDisplayArgs.dpi = singleAppDpi;
        int virtualDisplayWidth = virtualDisplayArgs.width;
        displaylinkState.imageReader = ImageReader.newInstance(virtualDisplayWidth, virtualDisplayArgs.height, 1, 2);
        displaylinkState.handlerThread = new HandlerThread("ImageAvailableListenerThread");
        displaylinkState.handlerThread.start();
        displaylinkState.handler = new Handler(displaylinkState.handlerThread.getLooper());

        displaylinkState.imageReader.setOnImageAvailableListener(new ListenImageReaderAndPostFrame(virtualDisplayArgs), displaylinkState.handler);
        Surface surface = displaylinkState.imageReader.getSurface();
        VirtualDisplay virtualDisplay = State.displaylinkState.getVirtualDisplay();
        if (virtualDisplay == null) {
            virtualDisplay = CreateVirtualDisplay.createVirtualDisplay(virtualDisplayArgs, surface);
            displaylinkState.createdVirtualDisplay(virtualDisplay);
            if (lastPackageName != null) {
                ServiceUtils.launchPackage(context, lastPackageName, virtualDisplay.getDisplay().getDisplayId());
            }
        } else {
            State.log("Reusing existing virtual display: " + virtualDisplay.getDisplay().getDisplayId());
            virtualDisplay.setSurface(surface);
        }
        int displayId = virtualDisplay.getDisplay().getDisplayId();
        InputRouting.moveImeToExternal(displayId);
        InputRouting.bindAllExternalInputToDisplay(displayId);
        new Handler().postDelayed(() -> {
            InputRouting.bindAllExternalInputToDisplay(displayId);
        }, 5000);
    }

    private void _copyFirmwares(Context context) {
        File srcDir = ApkImporter.assetsDir(context);
        if (!srcDir.exists()) {
            State.log("No imported firmware directory found");
            return;
        }
        File[] files = srcDir.listFiles();
        if (files == null) return;
        for (File src : files) {
            if (!src.getName().endsWith(".spkg")) continue;
            File dst = new File(context.getFilesDir(), src.getName());
            if (dst.exists() && dst.length() > 0) {
                State.log("Firmware file already exists, skipping: " + src.getName());
                continue;
            }
            try (InputStream in = new java.io.FileInputStream(src);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                State.log("Successfully copied firmware file: " + src.getName());
            } catch (IOException e) {
                State.log("Failed to copy firmware file: " + src.getName() + ", error: " + e.getMessage());
            }
        }
    }

    private boolean _requestUsbPermission(Context context, UsbManager usbManager, UsbDevice device) throws YieldException {
        if (usbManager.hasPermission(device)) {
            State.log("USB device permission already granted: " + device.getDeviceName());
        } else if (usbRequested) {
            State.log("Skipping task, USB device permission not granted: " + device.getDeviceName());
            return false;
        } else {
            usbRequested = true;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(SunshineService.ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, pendingIntent);
            throw new YieldException("Waiting for USB permission");
        }
        return true;
    }

    private void _openUsbConnection(Context context, UsbManager usbManager, DisplaylinkState displaylinkState) {
        if (displaylinkState.displaylinkDevice2 == null) {
            if (displaylinkState.usbConnection == null) {
                displaylinkState.usbConnection = usbManager.openDevice(displaylinkState.device);
                if (displaylinkState.usbConnection == null) {
                    throw new RuntimeException("Failed to open USB device connection");
                } else {
                    State.log("Successfully opened USB device connection");
                }
            } else {
                State.log("USB device connection already exists");
            }
        } else {
            if (displaylinkState.displaylinkConnection2 == null || displaylinkState.usbConnection == null || displaylinkState.usbConnection.getRawDescriptors() == null) {
                if (displaylinkState.usbConnection != null) {
                    displaylinkState.usbConnection.close();
                }
                displaylinkState.usbConnection = usbManager.openDevice(displaylinkState.device);
                if (displaylinkState.usbConnection == null) {
                    throw new RuntimeException("Failed to open USB device connection");
                } else {
                    State.log("Successfully opened USB device connection");
                }
                displaylinkState.displaylinkConnection2 = usbManager.openDevice(displaylinkState.displaylinkDevice2);
                if (displaylinkState.displaylinkConnection2 == null) {
                    throw new RuntimeException("Failed to open second USB device connection");
                } else {
                    State.log("Successfully opened second USB device connection");
                }
            } else {
                State.log("Second USB device connection already exists");
            }
        }
        if (displaylinkState.usbConnection.getRawDescriptors() == null) {
            throw new RuntimeException("USB connection failed to get raw descriptors");
        }
    }

    private boolean _requestDevice2UsbPermission(Context context, UsbManager usbManager, DisplaylinkState displaylinkState) throws YieldException {
        if (displaylinkState.displaylinkDevice2 == null) {
            for (UsbDevice device : usbManager.getDeviceList().values()) {
                if (device.getDeviceName().equals(displaylinkState.device.getDeviceName())) {
                    continue;
                }
                if (device.getVendorId() == 6121) {
                    displaylinkState.displaylinkDevice2 = device;
                    break;
                }
            }
        }
        if (displaylinkState.displaylinkDevice2 == null) {
            return true;
        }
        if (usbManager.hasPermission(displaylinkState.displaylinkDevice2)) {
            State.log("Second USB device permission already granted: " + displaylinkState.displaylinkDevice2.getDeviceName());
            return true;
        } else if (device2UsbRequested) {
            State.log("Skipping task, second USB device permission not granted: " + displaylinkState.displaylinkDevice2.getDeviceName());
            return false;
        }
        device2UsbRequested = true;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(SunshineService.ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        usbManager.requestPermission(displaylinkState.displaylinkDevice2, pendingIntent);
        throw new YieldException("Waiting for second USB permission");
    }
    private boolean _initializeNativeDriver(Context context, DisplaylinkState displaylinkState) throws YieldException {
        if (displaylinkState.displaylinkDevice2 != null && displaylinkState.monitorInfo == null) {
            if (displaylinkState.nativeDriver != null) {
                displaylinkState.nativeDriver.destroy();
                displaylinkState.nativeDriver = null;
            }
        }
        if (displaylinkState.nativeDriver == null) {
            displaylinkState.nativeDriver = new NativeDriver();
            displaylinkState.nativeDriverListener = new NativeDriverListener(deviceName);
            displaylinkState.nativeDriver.destroy();
            int resultCode = displaylinkState.nativeDriver.create(displaylinkState.nativeDriverListener, context.getFilesDir().toString(), true);
            if (resultCode != 0) {
                throw new RuntimeException("Failed to create NativeDriver: " + resultCode);
            } else {
                State.log("NativeDriver created successfully");
            }
            displaylinkState.nativeDriver.usbDeviceDetached(deviceName);
            if (displaylinkState.displaylinkDevice2 != null) {
                displaylinkState.nativeDriver.usbDeviceDetached(displaylinkState.displaylinkDevice2.getDeviceName());
            }
            resultCode = displaylinkState.nativeDriver.usbDeviceAttached(deviceName, displaylinkState.usbConnection.getFileDescriptor(), displaylinkState.usbConnection.getRawDescriptors(), displaylinkState.usbConnection.getRawDescriptors().length);
            if (resultCode != 0) {
                throw new RuntimeException("Failed to attach USB device: " + resultCode);
            } else {
                State.log("USB device attached successfully");
            }
            if (displaylinkState.displaylinkDevice2 != null) {
                resultCode = displaylinkState.nativeDriver.usbDeviceAttached(displaylinkState.displaylinkDevice2.getDeviceName(), displaylinkState.displaylinkConnection2.getFileDescriptor(), displaylinkState.displaylinkConnection2.getRawDescriptors(), displaylinkState.displaylinkConnection2.getRawDescriptors().length);
                if (resultCode != 0) {
                    throw new RuntimeException("Failed to attach second USB device: " + resultCode);
                } else {
                    State.log("Second USB device attached successfully");
                }
            }
        } else {
            State.log("NativeDriver already exists, skipping");
        }

        if (displaylinkState.monitorInfo == null) {
            State.log("No display info found, please connect a display and try again");
            return false;
        }
        return true;
    }

    private boolean _requestMediaProjectionPermission(Context context, DisplaylinkState displaylinkState) throws YieldException {
        if (State.displaylinkState.getVirtualDisplay() != null) {
            State.log("Virtual display already exists, skipping projection permission request");
            return true;
        }
        if (State.getMediaProjection() != null) {
            State.log("MediaProjection already exists, skipping");
            return true;
        }
        if (mediaProjectionRequested) {
            State.log("Skipping task, screen projection permission not granted");
            return false;
        }
        displaylinkState.stopVirtualDisplay();
        mediaProjectionRequested = true;
        MirrorMainActivity mirrorMainActivity = State.getCurrentActivity();
        if (mirrorMainActivity == null) {
            return false;
        }
        mirrorMainActivity.startMediaProjectionService();
        throw new YieldException("Waiting for projection permission");
    }
}