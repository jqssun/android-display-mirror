package io.github.jqssun.displaymirror.airplay;

import android.content.Context;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import io.github.jqssun.displaymirror.CastPlaceholderActivity;
import io.github.jqssun.displaymirror.MainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.job.CreateVirtualDisplay;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

public class AirPlayService {
  private static final String TAG = "AirPlayService";
  private static AirPlayService instance;
  private static final Handler mainHandler = new Handler(Looper.getMainLooper());

  public interface AirPlayListener {
    void onDeviceFound(String name, String ip, int port);

    void onConnected();

    void onDisconnected(String error);

    void onError(String error);

    void onPinRequired();
  }

  private airplaylib.Session session;
  private AirPlayListener listener;
  private final List<AirPlayDevice> devices = new ArrayList<>();
  private boolean connected;
  private AirPlayEncoder encoder;
  private AirPlayAudio audio;
  private MediaProjection projection;
  // pending connect params, used after projection is granted
  private String pendingHost;
  private int pendingPort;
  private String pendingPin;
  private int pendingWidth, pendingHeight, pendingFps;

  public static class AirPlayDevice {
    public String name;
    public String ip;
    public int port;

    public AirPlayDevice(String name, String ip, int port) {
      this.name = name;
      this.ip = ip;
      this.port = port;
    }

    @Override
    public String toString() {
      return name + " [" + ip + "]";
    }
  }

  public static AirPlayService getInstance() {
    if (instance == null) {
      instance = new AirPlayService();
    }
    return instance;
  }

  public void setListener(AirPlayListener listener) {
    this.listener = listener;
  }

  public List<AirPlayDevice> getDevices() {
    return devices;
  }

  public boolean isConnected() {
    return connected;
  }

  private void _ensureSession() {
    if (session != null) return;
    // pair-verify on later connects instead of re-pairing, which re-prompts for a PIN
    Context ctx = State.getContext();
    if (ctx != null) {
      try {
        airplaylib.Airplaylib.setCredentialsPath(
            new java.io.File(ctx.getFilesDir(), "airplay-credentials.json").getAbsolutePath());
      } catch (Exception e) {
        State.log("AirPlay: credential store unavailable: " + e.getMessage());
      }
    }
    airplaylib.Session s =
        airplaylib.Airplaylib.newSession(
            new airplaylib.EventHandler() {
              @Override
              public void onDeviceFound(String deviceJSON) {}

              @Override
              public void onConnected() {
                connected = true;
                State.log("AirPlay connected, requesting projection...");
                // now request projection: only after AirPlay handshake succeeded
                mainHandler.post(
                    () -> {
                      MainActivity activity = State.getCurrentActivity();
                      if (activity != null) {
                        activity.startAirPlayProjection();
                      } else {
                        State.log("AirPlay: no activity for projection request");
                      }
                      if (listener != null) listener.onConnected();
                    });
              }

              @Override
              public void onDisconnected(String err) {
                connected = false;
                _stopEncoder();
                mainHandler.post(
                    () -> {
                      if (listener != null) listener.onDisconnected(err);
                    });
                State.log("AirPlay disconnected: " + err);
              }

              @Override
              public void onPinRequired() {
                State.log("AirPlay PIN required");
                session = null;
                mainHandler.post(
                    () -> {
                      if (listener != null) listener.onPinRequired();
                    });
              }

              @Override
              public void onError(String err) {
                // reset session so next connect attempt starts fresh
                if (!connected) {
                  session = null;
                }
                mainHandler.post(
                    () -> {
                      if (listener != null) listener.onError(err);
                    });
                State.log("AirPlay error: " + err);
              }

              @Override
              public void onLog(String msg) {
                State.log(msg);
              }
            });
    session = s;
  }

  public void discover() {
    devices.clear();
    State.log("AirPlay: scanning for devices...");
    new Thread(
            () -> {
              try {
                InetAddress addr = _getWifiAddress();
                if (addr == null) {
                  State.log("AirPlay: no network address found");
                  return;
                }
                JmDNS jmdns = JmDNS.create(addr);
                jmdns.addServiceListener(
                    "_airplay._tcp.local.",
                    new ServiceListener() {
                      @Override
                      public void serviceAdded(ServiceEvent event) {
                        jmdns.requestServiceInfo(event.getType(), event.getName(), 3000);
                      }

                      @Override
                      public void serviceResolved(ServiceEvent event) {
                        String name = event.getName();
                        int port = event.getInfo().getPort();
                        String ip = null;
                        for (InetAddress a : event.getInfo().getInetAddresses()) {
                          if (a instanceof java.net.Inet4Address) {
                            ip = a.getHostAddress();
                            break;
                          }
                        }
                        if (ip == null) return;
                        String key = ip + ":" + port;
                        for (AirPlayDevice d : devices) {
                          if ((d.ip + ":" + d.port).equals(key)) return;
                        }
                        AirPlayDevice dev = new AirPlayDevice(name, ip, port);
                        devices.add(dev);
                        State.log("AirPlay: found " + name + " at " + ip + ":" + port);
                        final String devIp = ip;
                        mainHandler.post(
                            () -> {
                              if (listener != null) listener.onDeviceFound(name, devIp, port);
                            });
                      }

                      @Override
                      public void serviceRemoved(ServiceEvent event) {}
                    });

                Thread.sleep(5000);
                jmdns.close();
              } catch (Exception e) {
                Log.e(TAG, "discover failed", e);
                State.log("AirPlay discover error: " + e.getMessage());
              }
            })
        .start();
  }

  // step 1: User hits connect → start AirPlay handshake (no projection yet)
  public void connect(String host, int port, String pin, int width, int height, int fps) {
    // tear down any previous session/attempt
    if (session != null) {
      session.disconnect();
      session = null;
    }
    connected = false;

    pendingHost = host;
    pendingPort = port;
    pendingPin = pin;
    pendingFps = fps;

    // get screen dimensions now (doesn't need projection)
    Context ctx = State.getContext();
    if (ctx != null) {
      android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
      ((android.view.WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE))
          .getDefaultDisplay()
          .getRealMetrics(dm);
      pendingWidth = dm.widthPixels;
      pendingHeight = dm.heightPixels;
    }
    State.log(
        "AirPlay: connecting to "
            + host
            + ":"
            + port
            + " ("
            + pendingWidth
            + "x"
            + pendingHeight
            + ")");

    _ensureSession();
    session.connect(host, port, pin, pendingWidth, pendingHeight, pendingFps);
  }

  // step 2: projection ready
  public void onProjectionReady() {
    State.log("AirPlay: projection granted, starting encoder");
    // createForStream nulls State's slot
    projection = State.getMediaProjection();
    _startEncoder();
  }

  private void _startEncoder() {
    int width = session != null ? (int) session.streamWidth() : 0;
    int height = session != null ? (int) session.streamHeight() : 0;
    encoder = new AirPlayEncoder();
    encoder.start(width, height, pendingFps);
    if (session != null && encoder.screenWidth > 0 && encoder.screenHeight > 0) {
      session.setAirPlay1FrameSize(encoder.screenWidth, encoder.screenHeight);
    }
    if (session != null && session.hasAudio()) {
      audio = new AirPlayAudio();
      audio.start(projection);
    }
  }

  public void disconnect() {
    _stopEncoder();
    if (session != null) {
      session.disconnect();
      session = null;
    }
    connected = false;
  }

  private void _stopEncoder() {
    CastPlaceholderActivity.dismiss();
    if (audio != null) {
      audio.stop();
      audio = null;
    }
    if (encoder != null) {
      encoder.stop();
      encoder = null;
    }
    if (projection != null) {
      projection.stop();
      if (State.mediaProjectionInUse == projection) {
        State.mediaProjectionInUse = null;
      }
      projection = null;
    }
    CreateVirtualDisplay.restoreAspectRatio();
    CreateVirtualDisplay.powerOnScreen();
  }

  // called from native C++ via JNI for each Sunshine-encoded video frame
  // this is the piggyback path when Sunshine is also connected
  // name bound by JNI in airplay_bridge.cpp
  public static void onNativeVideoFrame(byte[] annexBData, boolean isKeyframe) {
    if (instance != null && instance.connected && instance.session != null) {
      instance.session.sendFrame(annexBData, isKeyframe);
    }
  }

  public static void onAudioFrame(byte[] pcm) {
    if (instance != null && instance.connected && instance.session != null) {
      instance.session.sendAudio(pcm);
    }
  }

  private static InetAddress _getWifiAddress() {
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface ni = interfaces.nextElement();
        if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
        Enumeration<InetAddress> addrs = ni.getInetAddresses();
        while (addrs.hasMoreElements()) {
          InetAddress a = addrs.nextElement();
          if (!a.isLoopbackAddress() && a instanceof java.net.Inet4Address) {
            return a;
          }
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "getWifiAddress", e);
    }
    return null;
  }
}
