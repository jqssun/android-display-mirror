package io.github.jqssun.displaymirror.shizuku;

public class ServiceManager {

    private static ActivityManager activityManager;

    public static ActivityManager getActivityManager() {
        if (activityManager == null) {
            activityManager = ActivityManager.create();
        }
        return activityManager;
    }
}
