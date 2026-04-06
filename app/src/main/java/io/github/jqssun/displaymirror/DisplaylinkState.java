package io.github.jqssun.displaymirror;

import android.hardware.display.VirtualDisplay;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;

import com.displaylink.manager.NativeDriver;
import com.displaylink.manager.NativeDriverListener;
import com.displaylink.manager.display.MonitorInfo;
import io.github.jqssun.displaymirror.job.VirtualDisplayArgs;

public class DisplaylinkState {
    public UsbDevice device;
    public UsbDeviceConnection usbConnection;
    public UsbDevice displaylinkDevice2;
    public UsbDeviceConnection displaylinkConnection2;
    public NativeDriver nativeDriver;
    public NativeDriverListener nativeDriverListener;
    public long encoderId = 0;
    public MonitorInfo monitorInfo;
    public ImageReader imageReader;
    public VirtualDisplayArgs virtualDisplayArgs = new VirtualDisplayArgs();
    private VirtualDisplay virtualDisplay;
    public HandlerThread handlerThread;
    public Handler handler;
    public float frameDuration = -1;

    public void stopHandlerThread() {
        if (handlerThread != null) {
            handlerThread.quitSafely();
            try {
                handlerThread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        handler = null;
        handlerThread = null;
    }

    public void stopImageReader() {
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    public void createdVirtualDisplay(VirtualDisplay virtualDisplay) {
        this.virtualDisplay = virtualDisplay;
    }

    public VirtualDisplay getVirtualDisplay() {
        return virtualDisplay;
    }   

    public void stopVirtualDisplay() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    public void destroy() {
        if (device == null) {
            return;
        }
        if (virtualDisplay != null) {
            virtualDisplay.setSurface(ImageReader.newInstance(1920, 1080, 1, 2).getSurface());
        }
        monitorInfo = null;
        encoderId = 0;
        stopHandlerThread();
        stopImageReader();
        if (nativeDriver != null) {
            State.log("Stopping nativeDriver");
            nativeDriver.usbDeviceDetached(device.getDeviceName());
            if (displaylinkDevice2 != null) {
                nativeDriver.usbDeviceDetached(displaylinkDevice2.getDeviceName());
            }
            nativeDriver.destroy();
            nativeDriver = null;
        }
        if (usbConnection != null) {
            usbConnection.close();
            usbConnection = null;
        }
        if (displaylinkConnection2 != null) {
            displaylinkConnection2.close();
            displaylinkConnection2 = null;
        }
    }
}
