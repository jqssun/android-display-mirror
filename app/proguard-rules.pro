-dontobfuscate

# Sunshine/AirPlay JNI
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
-keep class io.github.jqssun.displaymirror.job.SunshineMouse { *; }
-keep class io.github.jqssun.displaymirror.job.SunshineKeyboard { *; }
-keep class io.github.jqssun.displaymirror.job.AirPlayService { *; }
-keep class io.github.jqssun.displaymirror.job.AudioRecordProxy { *; }

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

-keep class io.github.jqssun.displaymirror.** extends android.os.Binder { *; }

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# JmDNS
-keep class javax.jmdns.** { *; }

# Shizuku
-keep class rikka.shizuku.** { *; }

# libsu
-keep class com.topjohnwu.superuser.** { *; }
