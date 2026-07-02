-dontobfuscate

# jni: job classes are reached via native FindClass
-keep class io.github.jqssun.displaymirror.job.** { *; }

# jni: keep every native method and its signature types
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
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
