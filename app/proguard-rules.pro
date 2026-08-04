-dontobfuscate

# jni: sunshine.cpp, airplay_bridge.cpp via FindClass
-keep class io.github.jqssun.displaymirror.sunshine.** { *; }
-keep class io.github.jqssun.displaymirror.airplay.** { *; }
-keep class io.github.jqssun.displaymirror.shizuku.** { *; }

# DisplayLink native driver 
-keep class com.displaylink.manager.** { *; }

# jni: keep every native method and its signature types
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}

# AIDL generated
-keep class * implements android.os.IInterface { *; }

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# JmDNS
-keep class javax.jmdns.** { *; }

# Shizuku
-keep class rikka.shizuku.** { *; }

# libsu
-keep class com.topjohnwu.superuser.** { *; }
