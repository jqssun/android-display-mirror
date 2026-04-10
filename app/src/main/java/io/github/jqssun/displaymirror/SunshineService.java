package io.github.jqssun.displaymirror;

import static android.app.Activity.RESULT_OK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import io.github.jqssun.displaymirror.job.MirrorDisplayMonitor;
import io.github.jqssun.displaymirror.job.MirrorDisplaylinkMonitor;
import io.github.jqssun.displaymirror.job.SunshineServer;
import io.github.jqssun.displaymirror.shizuku.PermissionManager;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import java.io.File;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

public class SunshineService extends Service {
    public static final String ACTION_USB_PERMISSION = "io.github.jqssun.displaymirror.USB_PERMISSION";
    public static SunshineService instance;
    private static final String CHANNEL_ID = "SunshineServiceChannel";
    private static final int NOTIFICATION_ID = 2;
    private static final String TAG = "SunshineService";

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            android.util.Log.d("SunshineService", "received action: " + intent.getAction());
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                State.resumeJob(State.MODE_DISPLAYLINK);
            }
        }
    };

    private int currentTimeout;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        releaseWakeLock();
        try {
            unregisterReceiver(usbPermissionReceiver);
        } catch (Exception e) {
            // ignore
        }
        State.unbindUserService();
    }

    public void releaseWakeLock() {
        if (currentTimeout > 0) {
            Settings.System.putInt(this.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, currentTimeout);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("data")) {
            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            Intent data = intent.getParcelableExtra("data");
            if (mediaProjectionManager == null || data == null) {
                State.log("Failed to get media projection service or data");
                return START_NOT_STICKY;
            }
            State.setMediaProjection(mediaProjectionManager.getMediaProjection(RESULT_OK, data));
            if (State.getMediaProjection() == null) {
                State.log("Failed to get media projection");
                return START_NOT_STICKY;
            }
            State.getMediaProjection().registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    super.onStop();
                    State.log("MediaProjection onStop callback");
                }
            }, null);
            State.resumeJob();
        } else {
            State.log("SunshineService received invalid authorization data");
            State.resumeJob();
        }
        if (Pref.getPreventAutoLock()) {
            preventAutoLock();
        }
        String sunshineName = "Mirror-"  + Build.MANUFACTURER + "-" + Build.MODEL;
        SunshineServer.setSunshineName(sunshineName);
        Set<String> ipAddresses = getAllWifiIpAddresses(this);
        probeH265();

        new Thread(() -> {
            try {
                SunshineServer.setFileStatePath(SunshineService.this.getFilesDir().getAbsolutePath() + "/sunshine_state.json");
                writeCertAndKey(SunshineService.this);
                List<JmDNS> dnsServers = new ArrayList<>();
                if(!ipAddresses.isEmpty()) {
                    for (String addr : ipAddresses) {
                        try {
                            JmDNS jmdns = JmDNS.create(InetAddress.getByName(addr));
                            dnsServers.add(jmdns);
                            ServiceInfo serviceInfo = ServiceInfo.create(
                                    "_nvstream._tcp.local.",
                                    "Mirror",
                                    47989,
                                    "Mirror"
                            );

                            jmdns.registerService(serviceInfo);
                            Log.i("SunshineService", "JmDNS service registered, IP: " + addr);
                        } catch (Exception e) {
                            Log.e("SunshineService", "Failed to register JmDNS service on IP " + addr, e);
                        }
                    }
                }
                new Thread(() -> { 
                    try {
                        SunshineServer.start();
                        for (JmDNS server : dnsServers) {
                            server.close();
                        }
                    } catch(Throwable e) {
                        Log.e("SunshineService", "thread quit", e);
                    }
                }).start();
                if (ipAddresses.isEmpty()) {
                    State.log("Unable to get WiFi IP address");
                } else {
                    State.log("Publishing Moonlight service name: "  + sunshineName);
                    for (String addr : ipAddresses) {
                        State.log("Publishing Moonlight IP: "  + addr);
                    }
                }
            } catch (Exception e) {
                Log.e("SunshineService", "Failed to initialize network service", e);
            }
        }).start();

        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, permissionFilter, null, null, Context.RECEIVER_EXPORTED);

        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        MirrorDisplayMonitor.init(displayManager);
        MirrorDisplaylinkMonitor.init(this);
        State.refreshMainActivity();
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            if (ShizukuUtils.hasPermission() && State.userService == null) {
                State.log("try start shizuku user service");
                State.bindUserService();
                handler.postDelayed(() -> {
                    if (ShizukuUtils.hasPermission() && State.userService == null) {
                        State.log("Shizuku user service failed to start, please revoke and re-grant Shizuku authorization. try start user service again");
                        State.unbindUserService();
                        State.bindUserService();
                    }
                }, 15 * 1000);
            }
        }, 2000);
        return START_NOT_STICKY;
    }

    private void preventAutoLock() {
        if (!ShizukuUtils.hasPermission()) {
            return;
        }
        if (PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
            currentTimeout = Settings.System.getInt(this.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, 0);
            Log.i("SunshineService", "Current screen timeout: " + currentTimeout + "ms");
            if (currentTimeout >= 4 * 60 * 60 * 1000) {
                currentTimeout = 15 * 1000;
            }
            Settings.System.putInt(this.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, 4 * 60 * 60 * 1000);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Sunshine Service Channel",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.mirror_app_name))
            .setContentText(getString(R.string.sunshine_service_running))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build();
    }

    private boolean probeH265() {
        try {
            android.media.MediaCodecList codecList = new android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS);
            for (android.media.MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
                if (!codecInfo.isHardwareAccelerated()) {
                    continue;
                }
                if (!codecInfo.isEncoder()) {
                    continue;
                }
                if (!isSupported(codecInfo, "video/hevc")) {
                    continue;
                }
                SunshineServer.enableH265();
                return true;
            }
            State.log("Device does not support H.265/HEVC encoding");
            return false;
        } catch (Exception e) {
            State.log("Error checking H.265 encoding support: " + e.getMessage());
            return false;
        }
    }

    private boolean isSupported(MediaCodecInfo codecInfo, String mime) {
        String[] types = codecInfo.getSupportedTypes();
        for (String type : types) {
            if (type.equalsIgnoreCase(mime)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> getAllWifiIpAddresses(Context context) {
        Set<String> ipAddresses = new HashSet<>();
        
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.isWifiEnabled() && wifiManager.getConnectionInfo() != null) {
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            if (ipAddress != 0) {
                // Convert little-endian to big-endian if needed
                byte[] bytes = new byte[4];
                bytes[0] = (byte) (ipAddress & 0xFF);
                bytes[1] = (byte) ((ipAddress >> 8) & 0xFF);
                bytes[2] = (byte) ((ipAddress >> 16) & 0xFF);
                bytes[3] = (byte) ((ipAddress >> 24) & 0xFF);

                try {
                    String ip = InetAddress.getByAddress(bytes).getHostAddress();
                    ipAddresses.add(ip);
                } catch (UnknownHostException e) {
                    Log.e(TAG, "Failed to get WiFi IP address", e);
                }
            }
        }
        
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    List<InterfaceAddress> interfaceAddresses = ni.getInterfaceAddresses();
                    for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                        if (interfaceAddress.getAddress() != null) {
                            String ip = interfaceAddress.getAddress().getHostAddress();
                            if (ip != null && ip.startsWith("192.168")) {
                                ipAddresses.add(ip);
                            }
                        }
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Failed to get network interface IP addresses", e);
        }
        
        return ipAddresses;
    }

    public static void writeCertAndKey(Context context) {
        File certFile = new File(context.getFilesDir(), "cacert.pem");
        File keyFile = new File(context.getFilesDir(), "cakey.pem");

        if (certFile.exists() && keyFile.exists()) {
            SunshineServer.setCertPath(certFile.getAbsolutePath());
            SunshineServer.setPkeyPath(keyFile.getAbsolutePath());
            return;
        }

        try {
            java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            java.security.KeyPair kp = kpg.generateKeyPair();

            javax.security.auth.x500.X500Principal dn = new javax.security.auth.x500.X500Principal("CN=Mirror");
            java.util.Date notBefore = new java.util.Date(System.currentTimeMillis() - 86400000L);
            java.util.Date notAfter = new java.util.Date(System.currentTimeMillis() + 20L * 365 * 86400000L);
            org.bouncycastle.cert.X509v3CertificateBuilder certBuilder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                    dn, java.math.BigInteger.valueOf(System.currentTimeMillis()), notBefore, notAfter, dn, kp.getPublic());
            org.bouncycastle.operator.ContentSigner signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSAEncryption").build(kp.getPrivate());
            java.security.cert.X509Certificate cert = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));

            String certPem = "-----BEGIN CERTIFICATE-----\n"
                    + android.util.Base64.encodeToString(cert.getEncoded(), android.util.Base64.DEFAULT)
                    + "-----END CERTIFICATE-----\n";
            // Java's getEncoded() returns PKCS#8; OpenSSL expects PKCS#1 for "RSA PRIVATE KEY"
            // Use PKCS#8 header which OpenSSL also accepts
            String keyPem = "-----BEGIN PRIVATE KEY-----\n"
                    + android.util.Base64.encodeToString(kp.getPrivate().getEncoded(), android.util.Base64.DEFAULT)
                    + "-----END PRIVATE KEY-----\n";

            try (java.io.FileWriter certWriter = new java.io.FileWriter(certFile)) {
                certWriter.write(certPem);
            }
            try (java.io.FileWriter keyWriter = new java.io.FileWriter(keyFile)) {
                keyWriter.write(keyPem);
            }

            SunshineServer.setCertPath(certFile.getAbsolutePath());
            SunshineServer.setPkeyPath(keyFile.getAbsolutePath());
            android.util.Log.i(TAG, "Generated keypair: " + context.getFilesDir().getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to generate keypair", e);
        }
    }
} 