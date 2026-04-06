package io.github.jqssun.displaymirror;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

import android.view.KeyEvent;

import androidx.annotation.NonNull;

import io.github.jqssun.displaymirror.shizuku.PermissionManager;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

public class TouchpadAccessibilityService extends AccessibilityService {
    private static TouchpadAccessibilityService instance;

    public static boolean isAccessibilityServiceEnabled(Context context) {
        String serviceName = context.getPackageName() + "/" + TouchpadAccessibilityService.class.getCanonicalName();
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (enabledServices != null) {
            return enabledServices.contains(serviceName);
        }
        return false;
    }

    public static void startServiceByShizuku(Context context) {
        if (TouchpadAccessibilityService.getInstance() != null) {
            return;
        }
        if (Pref.getDisableAccessibility()) {
            return;
        }
        if (PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
            String newService = BuildConfig.APPLICATION_ID + "/" + TouchpadAccessibilityService.class.getCanonicalName();
            String existingServices = getExistingServices(context, newService);

            String finalServices;
            // append new service if exists
            if (existingServices != null && !existingServices.isEmpty()) {
                finalServices = existingServices + ":" + newService;
            } else {
                finalServices = newService;
            }

            Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, finalServices);
            Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            Intent serviceIntent = new Intent(context, TouchpadAccessibilityService.class);
            context.startService(serviceIntent);
        }
    }

    public static void restartServiceByShizuku(Context context) {
        if (Pref.getDisableAccessibility()) {
            return;
        }
        if (PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
            String newService = BuildConfig.APPLICATION_ID + "/" + TouchpadAccessibilityService.class.getCanonicalName();
            Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, getExistingServices(context, newService));
            new Handler().postDelayed(() -> {
                startServiceByShizuku(context);
            }, 100);
        }
    }

    private static @NonNull String getExistingServices(Context context, String newService) {
        String existingServices = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(existingServices == null) {
            existingServices = "";
        }
        if (existingServices.contains(":" + newService)) {
            existingServices = existingServices.replace(":" + newService, "");
            Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, existingServices);
        } else if (existingServices.contains(newService + ":")) {
            existingServices = existingServices.replace(newService + ":", "");
            Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, existingServices);
        } else if (existingServices.contains(newService)) {
            existingServices = "";
            Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, existingServices);
        }
        return existingServices;
    }

    public static void disableAll(Context context) {
        if (PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
            Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");
            Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "0");
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        State.log("TouchpadAccessibilityService connected");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        State.log("TouchpadAccessibilityService disconnected");
        return super.onUnbind(intent);
    }

    public static TouchpadAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME && State.lastSingleAppDisplay > 0) {
            TouchpadActivity.launchSingleApp(getApplicationContext(), State.lastSingleAppDisplay);
            return true;
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    private List<AccessibilityNodeInfo> findFocusableNodes(AccessibilityNodeInfo root, List<AccessibilityNodeInfo> results) {
        if (root == null) return results;

        if (root.isFocusable()) {
            results.add(root);
            if (results.size() >= 3) {
                return results;
            }
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                findFocusableNodes(child, results);
                if (results.size() >= 3) {
                    return results;
                }
            }
        }

        return results;
    }

    public boolean setFocus(int displayId) {
        List<AccessibilityWindowInfo> targetDisplayWindows = null;
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            targetDisplayWindows = getWindows();
        } else {
            SparseArray<List<AccessibilityWindowInfo>> windows = getWindowsOnAllDisplays();
            targetDisplayWindows = windows.get(displayId);
        }
        android.util.Log.d("AccessibilityService", "Got window list: " + (targetDisplayWindows != null ? targetDisplayWindows.size() : 0) + " windows");

        if (targetDisplayWindows != null && !targetDisplayWindows.isEmpty()) {
            AccessibilityWindowInfo topWindow = null;
            int topLayer = -1;

            for (AccessibilityWindowInfo window : targetDisplayWindows) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    continue;
                }
                if (window.getDisplayId() == displayId) {
                    if (window.getLayer() > topLayer) {
                        topLayer = window.getLayer();
                        topWindow = window;
                    }
                }
            }
            if (topWindow == null) {
                android.util.Log.d("AccessibilityService", "Could not find top window");
                return false;
            }
            if (topWindow.isFocused()) {
                android.util.Log.d("AccessibilityService", "Already has focus, no need to set focus");
                return false;
            }
            android.util.Log.d("AccessibilityService", "Found top window, layer: " + topLayer);

            AccessibilityNodeInfo rootNode = topWindow.getRoot();
            android.util.Log.d("AccessibilityService", "Got root node: " + (rootNode != null ? "success" : "failed"));

            if (rootNode != null) {
                try {
                    List<AccessibilityNodeInfo> focusableNodes = findFocusableNodes(rootNode, new ArrayList<>());
                    android.util.Log.d("AccessibilityService", "Found " + focusableNodes.size() + " focusable nodes");

                    boolean focusSuccess = false;
                    for (AccessibilityNodeInfo node : focusableNodes) {
                        try {
                            boolean focusResult = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                            android.util.Log.d("AccessibilityService", "Attempted to set focus: " + (focusResult ? "success" : "failed"));
                            if (focusResult) {
                                focusSuccess = true;
                                break;
                            } else {
                                focusResult = node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                                if (focusResult) {
                                    focusSuccess = true;
                                    break;
                                } else {
                                    // add to blacklist
                                }
                            }
                        } catch (Throwable e) {
                            try {
                                boolean focusResult = node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                                if (focusResult) {
                                    focusSuccess = true;
                                    break;
                                }
                            } catch (Throwable e2) {
                                // ignore
                            }
                        }
                    }

                    try {
                        for (AccessibilityNodeInfo node : focusableNodes) {
                            node.recycle();
                        }
                    } catch (Throwable e) {
                        // ignore
                    }

                    if (focusSuccess) {
                        return true;
                    } else {
                        android.util.Log.d("AccessibilityService", "None of the nodes could receive focus");
                    }
                } finally {
                    try {
                        rootNode.recycle();
                    } catch (Throwable e) {
                        // ignore
                    }
                    android.util.Log.d("AccessibilityService", "Recycling root node");
                }
            }
        }
        return false;
    }

    public void performBackGesture(int displayId) {
        android.util.Log.d("AccessibilityService", "Performing back gesture, displayId: " + displayId);
        if (setFocus(displayId)) {
            boolean backResult = performGlobalAction(GLOBAL_ACTION_BACK);
            android.util.Log.d("AccessibilityService", "Back action: " + (backResult ? "success" : "failed"));
        }
    }

}