package io.github.jqssun.displaymirror.shizuku;

import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.IDisplayManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.Os;
import android.util.Log;
import android.view.Display;
import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import io.github.jqssun.displaymirror.job.AndroidVersions;
import rikka.shizuku.SystemServiceHelper;

public class UserService extends IUserService.Stub {
  private Context context;
  private boolean listenVolumeKey = false;
  private Process listenVolumeKeyProcess;
  private Thread volumeKeyThread;
  private AudioRecord audioRecord;
  private float[] buffer;

  public UserService() {
    _dropToShellIfRoot();
    Ln.i("Start UserService without context: " + android.os.Process.myUid());
  }

  @Keep
  public UserService(Context context) {
    this.context = context;
    _dropToShellIfRoot();
    Ln.i("Start UserService with context: " + android.os.Process.myUid());
  }

  private void _dropToShellIfRoot() {
    int uid = android.os.Process.myUid();
    if (uid != 0) return;
    int shellUid = android.os.Process.SHELL_UID;
    try {
      Os.setuid(shellUid);
      Ln.i("Dropped root to shell UID " + shellUid + " for trusted display compatibility");
    } catch (Exception e) {
      Ln.e("Failed to drop root to shell UID " + shellUid, e);
    }
  }

  /** reserved destroy method */
  @Override
  public void destroy() {
    Log.i("UserService", "destroy");
    stopListenVolumeKey();
    setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
    if (audioRecord != null) {
      audioRecord.stop();
    }
    System.exit(0);
  }

  @Override
  public void exit() {
    destroy();
  }

  @Override
  public void fetchLogs(ParcelFileDescriptor sink) throws RemoteException {
    try (java.io.OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(sink)) {
      Process process = new ProcessBuilder("logcat", "-d").redirectErrorStream(true).start();
      try (java.io.InputStream in = process.getInputStream()) {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
          out.write(buf, 0, n);
        }
      }
      out.flush();
      process.waitFor();
    } catch (Exception e) {
      Log.e("UserService", "logcat -d failed", e);
      throw new RemoteException("Failed to execute logcat -d: " + e.getMessage());
    }
  }

  @Override
  public String executeCommand(String command) throws RemoteException {
    Process process = null;
    try {
      process = new ProcessBuilder(command.split("\\s+")).redirectErrorStream(true).start();
      java.io.BufferedReader reader =
          new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));

      StringBuilder output = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
      }
      reader.close();
      process.waitFor();
      return output.toString();
    } catch (Exception e) {
      Log.e("UserService", "execute command failed: " + command, e);
      throw new RemoteException("Failed to execute command: " + command + " " + e.getMessage());
    } finally {
      if (process != null) {
        process.destroy();
      }
    }
  }

  public boolean setScreenPower(int powerMode) {
    Log.i("UserService", "try to setScreenPower: " + powerMode);
    if (Build.VERSION.SDK_INT >= 35 && powerMode == SurfaceControl.POWER_MODE_NORMAL) {
      setScreenPowerViaNewApi(SurfaceControl.POWER_MODE_OFF);
      setScreenPowerViaNewApi(SurfaceControl.POWER_MODE_NORMAL);
    }
    try {
      IBinder displayToken = getDisplayToken();
      if (displayToken == null) {
        return false;
      }
      Ln.d("setDisplayPowerMode: " + displayToken + " " + powerMode);
      boolean result = SurfaceControl.setDisplayPowerMode(displayToken, powerMode);
      Ln.d("after setDisplayPowerMode: " + result);
    } catch (Throwable e) {
      Ln.e("setScreenPower failed", e);
    }
    return true;
  }

  private boolean setScreenPowerViaNewApi(int powerMode) {
    IDisplayManager displayManager =
        IDisplayManager.Stub.asInterface(
            SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE));
    if (powerMode == SurfaceControl.POWER_MODE_OFF) {
      try {
        displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, false);
        Log.i("UserService", "requestDisplayPower by bool false");
      } catch (Throwable e) {
        Log.e("UserService", "failed to power off screen", e);
        try {
          displayManager.requestDisplayPower(
              Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_OFF);
          Log.i("UserService", "requestDisplayPower by int: " + powerMode);
        } catch (Throwable e2) {
          Log.e("UserService", "failed to power off screen", e2);
          return false;
        }
      }
    } else {
      try {
        displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, true);
        Log.i("UserService", "requestDisplayPower by bool true");
      } catch (Throwable e) {
        Log.e("UserService", "failed to power up screen", e);
        try {
          displayManager.requestDisplayPower(
              Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_NORMAL);
          Log.i("UserService", "requestDisplayPower by int: " + powerMode);
        } catch (Throwable e2) {
          Log.e("UserService", "failed to power up screen", e2);
          return false;
        }
      }
    }
    return true;
  }

  private @Nullable IBinder getDisplayToken() {
    try {
      long[] physicalDisplayIds = DisplayControl.getPhysicalDisplayIds();
      Ln.d("physicalDisplayIds count: " + physicalDisplayIds.length);
      if (physicalDisplayIds.length > 0) {
        return DisplayControl.getPhysicalDisplayToken(physicalDisplayIds[0]);
      }
      return SurfaceControl.getBuiltInDisplay();
    } catch (Throwable e) {
      Ln.e("failed to getDisplayToken", e);
      try {
        return SurfaceControl.getBuiltInDisplay();
      } catch (Throwable e2) {
        Ln.e("failed to getDisplayToken", e2);
      }
    }
    return null;
  }

  public void startListenVolumeKey() throws RemoteException {
    if (listenVolumeKey) {
      return;
    }
    listenVolumeKey = true;
    Thread thread =
        new Thread(
            () -> {
              while (listenVolumeKey) {
                try {
                  Ln.i("Run getevent to detect volume key pressed");
                  listenVolumeKeyProcess = Runtime.getRuntime().exec(new String[] {"getevent"});
                  java.io.BufferedReader reader =
                      new java.io.BufferedReader(
                          new java.io.InputStreamReader(listenVolumeKeyProcess.getInputStream()));
                  while (listenVolumeKey) {
                    String line = reader.readLine();
                    if (line == null || !listenVolumeKey) {
                      Ln.i("break out getevent");
                      break;
                    }
                    if (!line.endsWith("0000 0000 00000000")
                        && (line.endsWith("0001 0072 00000001")
                            || line.endsWith("0001 0073 00000001"))) {
                      Ln.i("detected volume key, try to power on screen");
                      setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
                      if (context != null) {
                        Intent intent =
                            new Intent("io.github.jqssun.displaymirror.EXIT_PURE_BLACK");
                        intent.setPackage("io.github.jqssun.displaymirror");
                        context.sendBroadcast(intent);
                      } else {
                        Ln.i("context is null, can not send EXIT_PURE_BLACK");
                      }
                    }
                  }
                  reader.close();
                  if (listenVolumeKeyProcess != null) {
                    listenVolumeKeyProcess.waitFor();
                    listenVolumeKeyProcess.destroyForcibly();
                  }
                } catch (Exception e) {
                  Ln.e("Listen volume key failed", e);
                }
                try {
                  Thread.sleep(1000);
                } catch (InterruptedException e) {
                  break;
                }
              }
              Ln.i("getevent thread end");
            });
    volumeKeyThread = thread;
    thread.start();
  }

  public void stopListenVolumeKey() {
    listenVolumeKey = false;
    if (listenVolumeKeyProcess != null) {
      listenVolumeKeyProcess.destroyForcibly();
      listenVolumeKeyProcess = null;
    }
    if (volumeKeyThread != null) {
      volumeKeyThread.interrupt();
      volumeKeyThread = null;
    }
  }

  @Override
  public boolean isRooted() throws RemoteException {
    return Os.getuid() == 0;
  }

  @Override
  public int readAudioFloat(float[] result) throws RemoteException {
    try {
      if (audioRecord == null) {
        return 0;
      }
      return audioRecord.read(result, 0, result.length, AudioRecord.READ_BLOCKING);
    } catch (Throwable e) {
      Ln.e("failed to read audio", e);
      return 0;
    }
  }

  @Override
  public int readAudioPcm16(byte[] result) throws RemoteException {
    try {
      // negative so callers can tell failure from silence
      if (audioRecord == null) {
        return AudioRecord.ERROR_INVALID_OPERATION;
      }
      return audioRecord.read(result, 0, result.length, AudioRecord.READ_BLOCKING);
    } catch (Throwable e) {
      Ln.e("failed to read audio", e);
      return AudioRecord.ERROR;
    }
  }

  @Override
  public boolean startRecordingAudio(int sampleRate, int encoding) throws RemoteException {
    long identity = Binder.clearCallingIdentity();
    try {
      if (audioRecord != null
          && (audioRecord.getSampleRate() != sampleRate
              || audioRecord.getAudioFormat() != encoding)) {
        Ln.d("recreating recorder for new format");
        stopRecordingAudio();
      }
      if (audioRecord == null) {
        Ln.d("before start recording");
        audioRecord = createAudioRecord(sampleRate, encoding);
        audioRecord.startRecording();
        Ln.d("started recording");
      }
      return true;
    } catch (Throwable e) {
      Ln.e("failed to start recording audio", e);
      return false;
    } finally {
      Binder.restoreCallingIdentity(identity);
    }
  }

  @Override
  public boolean stopRecordingAudio() throws RemoteException {
    try {
      if (audioRecord == null) {
        return true;
      } else {
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;
        return true;
      }
    } catch (Throwable e) {
      Ln.e("failed to stop recording audio", e);
      return false;
    }
  }

  // Shizuku can start us without context
  private Context _baseContext() {
    return context != null ? context : ActivityThread.systemMain().getSystemContext();
  }

  @Override
  public ParcelFileDescriptor lanDialTcp(String host, int port, int timeoutMs) {
    java.io.FileDescriptor fd = null;
    try {
      java.net.InetAddress addr = java.net.InetAddress.getByName(host);
      fd = _lanSocket(addr, android.system.OsConstants.SOCK_STREAM);
      if (fd == null) return null;
      // connect honours SO_SNDTIMEO on Linux; cleared afterwards so streaming writes never time out
      android.system.Os.setsockoptTimeval(
          fd,
          android.system.OsConstants.SOL_SOCKET,
          android.system.OsConstants.SO_SNDTIMEO,
          android.system.StructTimeval.fromMillis(Math.max(timeoutMs, 1)));
      android.system.Os.connect(fd, addr, port);
      android.system.Os.setsockoptTimeval(
          fd,
          android.system.OsConstants.SOL_SOCKET,
          android.system.OsConstants.SO_SNDTIMEO,
          android.system.StructTimeval.fromMillis(0));
      return _hand(fd);
    } catch (Exception e) {
      _closeQuietly(fd);
      throw new IllegalStateException(e.getMessage());
    }
  }

  @Override
  public ParcelFileDescriptor lanListenUdp(String host, int port) {
    java.io.FileDescriptor fd = null;
    try {
      java.net.InetAddress addr = java.net.InetAddress.getByName(host);
      fd = _lanSocket(addr, android.system.OsConstants.SOCK_DGRAM);
      if (fd == null) return null;
      java.net.InetAddress any =
          addr instanceof java.net.Inet6Address
              ? java.net.Inet6Address.getByName("::")
              : java.net.Inet4Address.getByName("0.0.0.0");
      android.system.Os.bind(fd, any, port);
      return _hand(fd);
    } catch (Exception e) {
      _closeQuietly(fd);
      throw new IllegalStateException(e.getMessage());
    }
  }

  // socket owned by this (shell/root) UID and pinned to the interface on host's subnet, so VPN uid routing and ingress filtering don't apply
  private static java.io.FileDescriptor _lanSocket(java.net.InetAddress addr, int type)
      throws Exception {
    String iface = _lanInterfaceFor(addr);
    if (iface == null) return null;
    int family =
        addr instanceof java.net.Inet6Address
            ? android.system.OsConstants.AF_INET6
            : android.system.OsConstants.AF_INET;
    java.io.FileDescriptor fd;
    // label the socket like the calling app; a socket carrying our own (su/shell) label is rejected by SELinux once handed over
    boolean labelled = _setSockCreateCon(_callerContext());
    try {
      fd = android.system.Os.socket(family, type, 0);
    } finally {
      if (labelled) _setSockCreateCon(null);
    }
    try {
      // hidden Os.setsockoptIfreq is the only SO_BINDTODEVICE path without JNI
      org.lsposed.hiddenapibypass.HiddenApiBypass.invoke(
          android.system.Os.class,
          null,
          "setsockoptIfreq",
          fd,
          android.system.OsConstants.SOL_SOCKET,
          25 /* SO_BINDTODEVICE */,
          iface);
    } catch (Throwable e) {
      _closeQuietly(fd);
      Throwable cause =
          e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null
              ? e.getCause()
              : e;
      throw new IllegalStateException("bind to " + iface + ": " + cause);
    }
    return fd;
  }

  private static String _callerContext() {
    int pid = Binder.getCallingPid();
    if (pid <= 0 || pid == android.os.Process.myPid()) return null;
    try (java.io.FileInputStream in = new java.io.FileInputStream("/proc/" + pid + "/attr/current")) {
      byte[] buf = new byte[256];
      int n = in.read(buf);
      if (n <= 0) return null;
      String ctx = new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8).trim();
      int nul = ctx.indexOf('\0');
      return nul >= 0 ? ctx.substring(0, nul) : ctx;
    } catch (Exception e) {
      return null;
    }
  }

  // null clears it; the attr is per-thread, so set and clear on the same binder thread
  private static boolean _setSockCreateCon(String ctx) {
    if (ctx == null) {
      ctx = "\n";
    } else if (ctx.isEmpty()) {
      return false;
    }
    try (java.io.FileOutputStream out =
        new java.io.FileOutputStream("/proc/thread-self/attr/sockcreate")) {
      out.write(ctx.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return true;
    } catch (Exception e) {
      Ln.w("sockcreate " + ctx.trim() + " failed: " + e.getMessage());
      return false;
    }
  }

  // interface whose subnet contains addr, never a VPN tunnel
  private static String _lanInterfaceFor(java.net.InetAddress addr) throws Exception {
    byte[] target = addr.getAddress();
    java.util.Enumeration<java.net.NetworkInterface> ifs =
        java.net.NetworkInterface.getNetworkInterfaces();
    while (ifs != null && ifs.hasMoreElements()) {
      java.net.NetworkInterface ni = ifs.nextElement();
      if (!ni.isUp() || ni.isLoopback() || ni.isPointToPoint()) continue;
      String name = ni.getName();
      if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("ipsec")) continue;
      for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
        byte[] local = ia.getAddress().getAddress();
        if (local.length != target.length) continue;
        if (_samePrefix(local, target, ia.getNetworkPrefixLength())) return name;
      }
    }
    return null;
  }

  private static boolean _samePrefix(byte[] a, byte[] b, int bits) {
    for (int i = 0; i < a.length && bits > 0; i++, bits -= 8) {
      int mask = bits >= 8 ? 0xff : (0xff << (8 - bits)) & 0xff;
      if ((a[i] & mask) != (b[i] & mask)) return false;
    }
    return true;
  }

  private static ParcelFileDescriptor _hand(java.io.FileDescriptor fd) throws java.io.IOException {
    try {
      return ParcelFileDescriptor.dup(fd);
    } finally {
      _closeQuietly(fd);
    }
  }

  private static void _closeQuietly(java.io.FileDescriptor fd) {
    if (fd == null) return;
    try {
      android.system.Os.close(fd);
    } catch (Exception ignored) {
    }
  }

  @SuppressLint({"WrongConstant", "MissingPermission"})
  private AudioRecord createAudioRecord(int sampleRate, int encoding) {
    AudioRecord.Builder builder = new AudioRecord.Builder();
    if (Build.VERSION.SDK_INT >= AndroidVersions.API_31_ANDROID_12) {
      // see FakeContext
      builder.setContext(new FakeContext(_baseContext()));
    }
    builder.setAudioSource(MediaRecorder.AudioSource.REMOTE_SUBMIX);
    int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    AudioFormat audioFormat =
        new AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build();
    builder.setAudioFormat(audioFormat);
    int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding);
    if (minBufferSize > 0) {
      // this buffer size does not impact latency
      builder.setBufferSizeInBytes(2 * minBufferSize);
    }
    return builder.build();
  }
}
