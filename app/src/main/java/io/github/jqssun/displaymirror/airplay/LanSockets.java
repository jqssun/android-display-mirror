package io.github.jqssun.displaymirror.airplay;

import android.os.ParcelFileDescriptor;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.shizuku.IUserService;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

/*
hands airplaylib sockets that reach the LAN while a VPN is up

a non-bypassable VPN routes the app UID into tun0 and the kernel drops LAN replies arriving on wlan0 for it, so receivers look dead; the Shizuku service runs as shell, which the VPN doesn't cover, and binds its sockets to the LAN interface before passing the fd back
*/
public class LanSockets implements airplaylib.SocketProvider {
  private static final long BIND_TIMEOUT_MS = 3000;
  private static boolean loggedUnavailable;

  public static void install() {
    airplaylib.Airplaylib.setSocketProvider(new LanSockets());
  }

  private static IUserService _service() {
    if (!VpnState.isActive()) return null;
    if (!ShizukuUtils.hasPermission()) {
      if (!loggedUnavailable) {
        loggedUnavailable = true;
        State.log("AirPlay: VPN is active; grant Shizuku so AirPlay can reach the local network");
      }
      return null;
    }
    IUserService svc = State.userService;
    if (svc == null) {
      State.bindUserService();
      svc = State.awaitUserService(BIND_TIMEOUT_MS);
    }
    return svc;
  }

  @Override
  public long dialTCP(String host, long port, long timeoutMs) throws Exception {
    IUserService svc = _service();
    if (svc == null) return -1;
    ParcelFileDescriptor pfd;
    try {
      pfd = svc.lanDialTcp(host, (int) port, (int) timeoutMs);
    } catch (IllegalStateException e) {
      throw new Exception(e.getMessage());
    }
    if (pfd == null) return -1;
    return pfd.detachFd();
  }

  @Override
  public long listenUDP(String host, long port) throws Exception {
    IUserService svc = _service();
    if (svc == null) return -1;
    ParcelFileDescriptor pfd;
    try {
      pfd = svc.lanListenUdp(host, (int) port);
    } catch (IllegalStateException e) {
      throw new Exception(e.getMessage());
    }
    if (pfd == null) return -1;
    return pfd.detachFd();
  }
}
