package io.github.jqssun.displaymirror.shizuku;

import android.content.pm.PackageManager;

import rikka.shizuku.Shizuku;

public class ShizukuUtils {
    public static boolean hasPermission() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasShizukuStarted() {
        try {
            Shizuku.checkSelfPermission();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static int getServerUid() {
        try {
            return Shizuku.getUid();
        } catch (Exception e) {
            return -1;
        }
    }
}
