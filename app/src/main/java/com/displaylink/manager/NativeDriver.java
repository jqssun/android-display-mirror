package com.displaylink.manager;

import android.util.Log;
import android.view.Surface;
import com.displaylink.manager.display.DisplayMode;
import java.io.File;
import java.nio.ByteBuffer;

public class NativeDriver {
    private static boolean loaded = false;

    public static void load(File libDir) {
        if (loaded) return;
        // load dependencies first, then the main lib
        System.load(new File(libDir, "libusb_android.so").getAbsolutePath());
        System.load(new File(libDir, "libAndroidDLM.so").getAbsolutePath());
        System.load(new File(libDir, "libDisplayLinkManager.so").getAbsolutePath());
        loaded = true;
        Log.i("displaylink", "loaded DisplayLinkManager from " + libDir);
    }

    public native int create(NativeDriverListener nativeDriverListener, String str, boolean z);

    public native long createImageReader(long j, DisplayMode displayMode, boolean z, boolean z2);

    public native void destroy();

    public native void destroyImageReader(long j);

    public native Surface getImageReaderSurface(long j);

    public native boolean isValid();

    public native void log(String str, int i, String str2);

    public native void notifyEvent(int i);

    public native int postFrame(long encoderId, ByteBuffer byteBuffer);

    public native boolean receiveDdcCiData(long j, byte[] bArr, int i);

    public native boolean sendDdcCiData(long j, byte[] bArr, int i);

    public native void setMode(long j, DisplayMode displayMode, int i, int i2);

    public native void setProtection(long j, boolean z);

    public native int usbDeviceAttached(String str, int i, byte[] bArr, int i2);

    public native void usbDeviceDetached(String str);
}
