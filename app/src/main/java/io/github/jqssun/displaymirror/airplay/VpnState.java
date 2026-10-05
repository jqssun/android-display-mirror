package io.github.jqssun.displaymirror.airplay;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import io.github.jqssun.displaymirror.State;

public class VpnState {
  // Wi-Fi/Ethernet network underneath any VPN, null if none
  public static Network lanNetwork() {
    Context ctx = State.getContext();
    if (ctx == null) return null;
    try {
      ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
      if (cm == null) return null;
      for (Network n : cm.getAllNetworks()) {
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
          return n;
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  public static boolean isActive() {
    Context ctx = State.getContext();
    if (ctx == null) return false;
    try {
      ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
      if (cm == null) return false;
      Network n = cm.getActiveNetwork();
      if (n == null) return false;
      NetworkCapabilities caps = cm.getNetworkCapabilities(n);
      return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    } catch (Exception e) {
      return false;
    }
  }
}
