package io.github.jqssun.displaymirror.job;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;
import java.io.File;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

public class SunshineHost {
  private static final String TAG = "SunshineHost";
  private static volatile boolean running;

  public static boolean isRunning() {
    return running;
  }

  public static void start(Context context) {
    if (running) {
      State.log("Sunshine server already running");
      return;
    }
    running = true;

    String name = Pref.getSunshineDeviceName();
    SunshineServer.setSunshineName(name);
    Set<String> ips = getWifiIpAddresses(context);
    probeH265();

    new Thread(
            () -> {
              try {
                SunshineServer.setFileStatePath(
                    context.getFilesDir().getAbsolutePath() + "/sunshine_state.json");
                writeCertAndKey(context);
                List<JmDNS> dnsServers = new java.util.ArrayList<>();
                for (String addr : ips) {
                  try {
                    JmDNS jmdns = JmDNS.create(InetAddress.getByName(addr));
                    dnsServers.add(jmdns);
                    jmdns.registerService(
                        ServiceInfo.create("_nvstream._tcp.local.", "Mirror", 47989, "Mirror"));
                    Log.i(TAG, "JmDNS service registered, IP: " + addr);
                  } catch (Exception e) {
                    Log.e(TAG, "Failed to register JmDNS service on IP " + addr, e);
                  }
                }
                new Thread(
                        () -> {
                          try {
                            SunshineServer.start();
                            for (JmDNS server : dnsServers) {
                              server.close();
                            }
                          } catch (Throwable e) {
                            Log.e(TAG, "server thread quit", e);
                          }
                        })
                    .start();
                if (ips.isEmpty()) {
                  State.log("unable to get WiFi IP address");
                } else {
                  State.log("publishing Sunshine service name: " + name);
                  for (String addr : ips) {
                    State.log("publishing Sunshine IP: " + addr);
                  }
                }
              } catch (Exception e) {
                Log.e(TAG, "Failed to initialize network service", e);
              }
            })
        .start();
  }

  public static Set<String> getWifiIpAddresses(Context context) {
    Set<String> ips = new HashSet<>();
    WifiManager wifiManager =
        (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    if (wifiManager != null
        && wifiManager.isWifiEnabled()
        && wifiManager.getConnectionInfo() != null) {
      int ip = wifiManager.getConnectionInfo().getIpAddress();
      if (ip != 0) {
        byte[] bytes =
            new byte[] {
              (byte) (ip & 0xFF), (byte) (ip >> 8 & 0xFF), (byte) (ip >> 16 & 0xFF), (byte) (ip >> 24 & 0xFF)
            };
        try {
          ips.add(InetAddress.getByAddress(bytes).getHostAddress());
        } catch (UnknownHostException e) {
          Log.e(TAG, "Failed to get WiFi IP address", e);
        }
      }
    }
    try {
      Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
      while (nis.hasMoreElements()) {
        NetworkInterface ni = nis.nextElement();
        if (!ni.isUp() || ni.isLoopback()) continue;
        for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
          if (ia.getAddress() == null) continue;
          String ip = ia.getAddress().getHostAddress();
          if (ip != null && ip.startsWith("192.168")) {
            ips.add(ip);
          }
        }
      }
    } catch (SocketException e) {
      Log.e(TAG, "Failed to get network interface IP addresses", e);
    }
    return ips;
  }

  private static void probeH265() {
    try {
      MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
      for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !codecInfo.isHardwareAccelerated()) {
          continue;
        }
        if (!codecInfo.isEncoder()) continue;
        for (String type : codecInfo.getSupportedTypes()) {
          if (type.equalsIgnoreCase("video/hevc")) {
            SunshineServer.enableH265();
            return;
          }
        }
      }
      State.log("device does not support H.265/HEVC encoding");
    } catch (Exception e) {
      State.log("error checking H.265 encoding support: " + e.getMessage());
    }
  }

  private static void writeCertAndKey(Context context) {
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
      javax.security.auth.x500.X500Principal dn =
          new javax.security.auth.x500.X500Principal("CN=Mirror");
      java.util.Date notBefore = new java.util.Date(System.currentTimeMillis() - 86400000L);
      java.util.Date notAfter =
          new java.util.Date(System.currentTimeMillis() + 20L * 365 * 86400000L);
      org.bouncycastle.cert.X509v3CertificateBuilder certBuilder =
          new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
              dn,
              java.math.BigInteger.valueOf(System.currentTimeMillis()),
              notBefore,
              notAfter,
              dn,
              kp.getPublic());
      org.bouncycastle.operator.ContentSigner signer =
          new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSAEncryption")
              .build(kp.getPrivate());
      java.security.cert.X509Certificate cert =
          new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
              .getCertificate(certBuilder.build(signer));
      String certPem =
          "-----BEGIN CERTIFICATE-----\n"
              + android.util.Base64.encodeToString(cert.getEncoded(), android.util.Base64.DEFAULT)
              + "-----END CERTIFICATE-----\n";
      // getEncoded() returns PKCS#8; OpenSSL accepts the PKCS#8 header too
      String keyPem =
          "-----BEGIN PRIVATE KEY-----\n"
              + android.util.Base64.encodeToString(
                  kp.getPrivate().getEncoded(), android.util.Base64.DEFAULT)
              + "-----END PRIVATE KEY-----\n";
      try (java.io.FileWriter w = new java.io.FileWriter(certFile)) {
        w.write(certPem);
      }
      try (java.io.FileWriter w = new java.io.FileWriter(keyFile)) {
        w.write(keyPem);
      }
      SunshineServer.setCertPath(certFile.getAbsolutePath());
      SunshineServer.setPkeyPath(keyFile.getAbsolutePath());
      Log.i(TAG, "Generated keypair: " + context.getFilesDir().getAbsolutePath());
    } catch (Exception e) {
      Log.e(TAG, "Failed to generate keypair", e);
    }
  }
}
