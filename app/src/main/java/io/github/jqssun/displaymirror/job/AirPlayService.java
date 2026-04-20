package io.github.jqssun.displaymirror.job;

import android.content.Context;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.github.jqssun.displaymirror.MirrorMainActivity;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;

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
    }

    private airplaylib.Session session;
    private AirPlayListener listener;
    private final List<AirPlayDevice> devices = new ArrayList<>();
    private boolean connected;
    private AirPlayEncoder encoder;
    private MediaProjection pendingProjection;
    // Pending connect params, used after projection is granted
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
        airplaylib.Session s = airplaylib.Airplaylib.newSession(new airplaylib.EventHandler() {
            @Override
            public void onDeviceFound(String deviceJSON) {}

            @Override
            public void onConnected() {
                connected = true;
                State.log("AirPlay connected, requesting projection...");
                // Now request projection — only after AirPlay handshake succeeded
                mainHandler.post(() -> {
                    MirrorMainActivity activity = State.getCurrentActivity();
                    if (activity != null) {
                        activity.requestAirPlayProjection();
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
                mainHandler.post(() -> {
                    if (listener != null) listener.onDisconnected(err);
                });
                State.log("AirPlay disconnected: " + err);
            }

            @Override
            public void onPinRequired() {
                State.log("AirPlay PIN required");
            }

            @Override
            public void onError(String err) {
                // Reset session so next connect attempt starts fresh
                if (!connected) {
                    session = null;
                }
                mainHandler.post(() -> {
                    if (listener != null) listener.onError(err);
                });
                State.log("AirPlay error: " + err);
            }
        });
        session = s;
    }

    public void discover() {
        devices.clear();
        State.log("AirPlay: scanning for devices...");
        new Thread(() -> {
            try {
                InetAddress addr = _getWifiAddress();
                if (addr == null) {
                    State.log("AirPlay: no network address found");
                    return;
                }
                JmDNS jmdns = JmDNS.create(addr);
                jmdns.addServiceListener("_airplay._tcp.local.", new ServiceListener() {
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
                        mainHandler.post(() -> {
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
        }).start();
    }

    // Step 1: User hits connect → start AirPlay handshake (no projection yet)
    public void connect(String host, int port, String pin, int width, int height, int fps) {
        // Tear down any previous session/attempt
        if (session != null) {
            session.disconnect();
            session = null;
        }
        connected = false;

        pendingHost = host;
        pendingPort = port;
        pendingPin = pin;
        pendingFps = fps;

        // Get screen dimensions now (doesn't need projection)
        Context ctx = State.getContext();
        if (ctx != null) {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            ((android.view.WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRealMetrics(dm);
            pendingWidth = dm.widthPixels;
            pendingHeight = dm.heightPixels;
        }
        State.log("AirPlay: connecting to " + host + ":" + port + " (" + pendingWidth + "x" + pendingHeight + ")");

        airplaylib.Airplaylib.setAppleReceiver(Pref.getAirPlayAppleReceiver());
        _ensureSession();
        session.connect(host, port, pin, pendingWidth, pendingHeight, pendingFps);
    }

    // Step 2: Called from AirPlayForegroundService after projection is granted
    public void onProjectionReady(MediaProjection projection) {
        State.log("AirPlay: projection granted, starting encoder");
        pendingProjection = projection;
        _startEncoder();
    }

    private void _startEncoder() {
        if (pendingProjection == null) {
            State.log("AirPlay: no projection for encoder");
            return;
        }
        encoder = new AirPlayEncoder();
        encoder.start(pendingProjection, pendingFps);
        pendingProjection = null;
    }

    public void sendFrame(byte[] annexBData, boolean isKeyframe) {
        if (session == null || !connected) return;
        session.sendFrame(annexBData, isKeyframe);
    }

    public void disconnect() {
        _stopEncoder();
        pendingProjection = null;
        if (session != null) {
            session.disconnect();
            session = null;
        }
        connected = false;
    }

    private void _stopEncoder() {
        if (encoder != null) {
            encoder.stop();
            encoder = null;
        }
    }

    // Called from native C++ via JNI for each Sunshine-encoded video frame
    // This is the piggyback path when Moonlight is also connected
    public static void onNativeVideoFrame(byte[] annexBData, boolean isKeyframe) {
        if (instance != null && instance.connected && instance.session != null) {
            instance.session.sendFrame(annexBData, isKeyframe);
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
