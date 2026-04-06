package io.github.jqssun.displaymirror.shizuku;


import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Application;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Looper;
import android.os.Parcel;

import io.github.jqssun.displaymirror.job.AndroidVersions;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SuppressLint("PrivateApi,BlockedPrivateApi,SoonBlockedPrivateApi,DiscouragedPrivateApi")
public final class Workarounds {

    private static final Class<?> ACTIVITY_THREAD_CLASS;
    private static final Object ACTIVITY_THREAD;

    static {
        prepareMainLooper();

        try {
            // ActivityThread activityThread = new ActivityThread();
            ACTIVITY_THREAD_CLASS = Class.forName("android.app.ActivityThread");
            Constructor<?> activityThreadConstructor = ACTIVITY_THREAD_CLASS.getDeclaredConstructor();
            activityThreadConstructor.setAccessible(true);
            ACTIVITY_THREAD = activityThreadConstructor.newInstance();

            // ActivityThread.sCurrentActivityThread = activityThread;
            Field sCurrentActivityThreadField = ACTIVITY_THREAD_CLASS.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            sCurrentActivityThreadField.set(null, ACTIVITY_THREAD);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Workarounds() {
        // not instantiable
    }

    public static void apply() {
        if (Build.VERSION.SDK_INT >= AndroidVersions.API_31_ANDROID_12) {
            // On some Samsung devices, DisplayManagerGlobal.getDisplayInfoLocked() calls ActivityThread.currentActivityThread().getConfiguration(),
            // which requires a non-null ConfigurationController.
            // ConfigurationController was introduced in Android 12, so do not attempt to set it on lower versions.
            // <https://github.com/Genymobile/scrcpy/issues/4467>
            // Must be called before fillAppContext() because it is necessary to get a valid system context.
            fillConfigurationController();
        }

        // On ONYX devices, fillAppInfo() breaks video mirroring:
        // <https://github.com/Genymobile/scrcpy/issues/5182>
        boolean mustFillAppInfo = !Build.BRAND.equalsIgnoreCase("ONYX");

        if (mustFillAppInfo) {
            fillAppInfo();
        }

        fillAppContext();
    }

    @SuppressWarnings("deprecation")
    private static void prepareMainLooper() {
        // Some devices internally create a Handler when creating an input Surface, causing an exception:
        //   "Can't create handler inside thread that has not called Looper.prepare()"
        // <https://github.com/Genymobile/scrcpy/issues/240>
        //
        // Use Looper.prepareMainLooper() instead of Looper.prepare() to avoid a NullPointerException:
        //   "Attempt to read from field 'android.os.MessageQueue android.os.Looper.mQueue'
        //    on a null object reference"
        // <https://github.com/Genymobile/scrcpy/issues/921>
        try {
            Looper.prepareMainLooper();
        } catch(Throwable e) {
            // ignore;
        }
    }

    private static void fillAppInfo() {
        try {
            // ActivityThread.AppBindData appBindData = new ActivityThread.AppBindData();
            Class<?> appBindDataClass = Class.forName("android.app.ActivityThread$AppBindData");
            Constructor<?> appBindDataConstructor = appBindDataClass.getDeclaredConstructor();
            appBindDataConstructor.setAccessible(true);
            Object appBindData = appBindDataConstructor.newInstance();

            ApplicationInfo applicationInfo = new ApplicationInfo();
            applicationInfo.packageName = FakeContext.PACKAGE_NAME;

            // appBindData.appInfo = applicationInfo;
            Field appInfoField = appBindDataClass.getDeclaredField("appInfo");
            appInfoField.setAccessible(true);
            appInfoField.set(appBindData, applicationInfo);

            // activityThread.mBoundApplication = appBindData;
            Field mBoundApplicationField = ACTIVITY_THREAD_CLASS.getDeclaredField("mBoundApplication");
            mBoundApplicationField.setAccessible(true);
            mBoundApplicationField.set(ACTIVITY_THREAD, appBindData);
        } catch (Throwable throwable) {
            // this is a workaround, so failing is not an error
            Ln.d("Could not fill app info: " + throwable.getMessage());
        }
    }

    private static void fillAppContext() {
        try {
            Application app = new Application();
            Field baseField = ContextWrapper.class.getDeclaredField("mBase");
            baseField.setAccessible(true);
            baseField.set(app, FakeContext.get());

            // activityThread.mInitialApplication = app;
            Field mInitialApplicationField = ACTIVITY_THREAD_CLASS.getDeclaredField("mInitialApplication");
            mInitialApplicationField.setAccessible(true);
            mInitialApplicationField.set(ACTIVITY_THREAD, app);
        } catch (Throwable throwable) {
            // this is a workaround, so failing is not an error
            Ln.d("Could not fill app context: " + throwable.getMessage());
        }
    }

    private static void fillConfigurationController() {
        try {
            Class<?> configurationControllerClass = Class.forName("android.app.ConfigurationController");
            Class<?> activityThreadInternalClass = Class.forName("android.app.ActivityThreadInternal");
            Constructor<?> configurationControllerConstructor = configurationControllerClass.getDeclaredConstructor(activityThreadInternalClass);
            configurationControllerConstructor.setAccessible(true);
            Object configurationController = configurationControllerConstructor.newInstance(ACTIVITY_THREAD);

            Field configurationControllerField = ACTIVITY_THREAD_CLASS.getDeclaredField("mConfigurationController");
            configurationControllerField.setAccessible(true);
            configurationControllerField.set(ACTIVITY_THREAD, configurationController);
        } catch (Throwable throwable) {
            Ln.d("Could not fill configuration: " + throwable.getMessage());
        }
    }

    static Context getSystemContext() {
        try {
            Method getSystemContextMethod = ACTIVITY_THREAD_CLASS.getDeclaredMethod("getSystemContext");
            return (Context) getSystemContextMethod.invoke(ACTIVITY_THREAD);
        } catch (Throwable throwable) {
            // this is a workaround, so failing is not an error
            Ln.d("Could not get system context: " + throwable.getMessage());
            return null;
        }
    }
}