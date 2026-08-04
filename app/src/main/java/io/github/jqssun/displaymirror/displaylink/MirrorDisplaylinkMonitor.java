package io.github.jqssun.displaymirror.displaylink;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import io.github.jqssun.displaymirror.MainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.job.CreateVirtualDisplay;
import io.github.jqssun.displaymirror.job.VirtualDisplayArgs;

public class MirrorDisplaylinkMonitor {

  public static final String ACTION_USB_PERMISSION =
      "io.github.jqssun.displaymirror.USB_PERMISSION";

  private static boolean registered = false;
  // tracked DisplayLink usb device, null while none attached
  private static String deviceName;

  private static final BroadcastReceiver usbPermissionReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
            State.resumeJob(DisplaylinkState.MODE);
          }
        }
      };

  private static final BroadcastReceiver usbDetachedReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          android.util.Log.d("MainActivity", "received action: " + intent.getAction());
          String action = intent.getAction();
          if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            MirrorDisplaylinkMonitor.onUsbDeviceDetached(device);
          }
        }
      };

  private static final BroadcastReceiver usbAttachedReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          android.util.Log.d("MainActivity", "received action: " + intent.getAction());
          String action = intent.getAction();
          if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            MirrorDisplaylinkMonitor.onUsbDeviceAttached(context, device);
          }
        }
      };

  public static void init(Context context) {
    UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
      handleDisplaylink(context, usbDevice);
    }
    if (registered) {
      return;
    }
    registered = true;
    Context appContext = context.getApplicationContext();
    _register(appContext, usbDetachedReceiver, UsbManager.ACTION_USB_DEVICE_DETACHED);
    _register(appContext, usbAttachedReceiver, UsbManager.ACTION_USB_DEVICE_ATTACHED);
    _register(appContext, usbPermissionReceiver, ACTION_USB_PERMISSION);
  }

  private static void _register(Context context, BroadcastReceiver receiver, String action) {
    context.registerReceiver(
        receiver, new IntentFilter(action), null, null, Context.RECEIVER_EXPORTED);
  }

  public static void handleDisplaylink(Context context, UsbDevice device) {
    if (device == null) {
      return;
    }
    if (deviceName != null) {
      if (context != null) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager.getDeviceList().get(deviceName) == null) {
          DisplaylinkState.instance.destroy();
          deviceName = null;
          DisplaylinkState.instance.device = null;
        }
      }
    }
    if (device.getVendorId() == 6121 && deviceName == null) {
      deviceName = device.getDeviceName();
      DisplaylinkState.instance.device = device;
      State.log("found DisplayLink device: " + device.getProductName());
    }
    if (device.getDeviceName().equals(deviceName)) {
      DisplaylinkState.instance.virtualDisplayArgs =
          new VirtualDisplayArgs(
              "DisplayLink",
              Pref.getDisplaylinkWidth(),
              Pref.getDisplaylinkHeight(),
              Pref.getDisplaylinkRefreshRate(),
              160,
              Pref.getRotateWithContent());
      State.startNewJob(
          DisplaylinkState.MODE,
          new ProjectViaDisplaylink(device, DisplaylinkState.instance.virtualDisplayArgs));
    }
  }

  public static void onUsbDeviceAttached(Context context, UsbDevice device) {
    if (device == null) {
      return;
    }
    handleDisplaylink(context, device);
  }

  public static void onUsbDeviceDetached(UsbDevice device) {
    if (device != null && device.getDeviceName().equals(deviceName)) {
      State.log("DisplayLink device disconnected: " + device.getProductName());
      DisplaylinkState.instance.destroy();
      deviceName = null;
      DisplaylinkState.instance.device = null;
      CreateVirtualDisplay.powerOnScreen();
    }
  }
}
