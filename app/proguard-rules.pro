# Sunshine JNI - native methods and callbacks from C++
-keep class io.github.jqssun.displaymirror.job.SunshineServer {
    native <methods>;
    # Called from native code
    public static void onPinRequested();
    public static void createVirtualDisplay(int, int, int, int, android.view.Surface, boolean);
    public static void stopVirtualDisplay();
    public static void showEncoderError(java.lang.String);
    public static void onMirrorClientDiscovered(java.lang.String);
    public static void setMirrorServerUuid(java.lang.String);
}

# DisplayLink native driver
-keep class com.displaylink.manager.NativeDriver { *; }
-keep class com.displaylink.manager.NativeDriverListener { *; }
-keep class com.displaylink.manager.display.DisplayMode { *; }
-keep class com.displaylink.manager.display.MonitorInfo { *; }

# Shizuku UserService (instantiated by Shizuku via reflection)
-keep class io.github.jqssun.displaymirror.shizuku.UserService { *; }
-keep class io.github.jqssun.displaymirror.shizuku.IUserService { *; }
-keep class io.github.jqssun.displaymirror.shizuku.IUserService$Stub { *; }

# AIDL generated
-keep class * implements android.os.IInterface { *; }

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# NanoHTTPD
-keep class org.nanohttpd.** { *; }

# JmDNS
-keep class javax.jmdns.** { *; }

# Shizuku
-keep class rikka.shizuku.** { *; }

# ZXing
-keep class com.google.zxing.** { *; }

# libsu
-keep class com.topjohnwu.superuser.** { *; }
