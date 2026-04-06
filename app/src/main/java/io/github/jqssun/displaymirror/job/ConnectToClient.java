package io.github.jqssun.displaymirror.job;

import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import android.util.Log;

public class ConnectToClient {
    public static void connect(int pin) {
        SunshineServer.pinCandidate = String.valueOf(pin);
        String clientIpAndPort = Pref.getSelectedClient();
        String[] parts = clientIpAndPort.split(":");
        String clientIp = parts[0];
        int clientPort = -1;
        try {
            clientPort = Integer.parseInt(parts[1]);
        } catch(Exception e) {
            State.log("Invalid client address: " + clientIpAndPort + "  " + e);
            return;
        }
        String serverIp = findServerIpInSameSubnet(clientIp);
        if (serverIp == null) {
            State.log("Cannot find local IP on the same subnet as client");
            return;
        }
        if (State.serverUuid == null) {
            State.log("ServerUuid is empty");
            return;
        }
        String request = "{\"action\": \"connect\", \"ip\": \"" + serverIp + "\", \"pin\": \"" + pin + "\", \"uuid\": \"" + State.serverUuid + "\"}\n";
        State.log("Sending auto-start request to: " + clientIp + ":" + clientPort);
        
        final String finalClientIp = clientIp;
        final int finalClientPort = clientPort;
        final String finalRequest = request;
        
        new Thread(() -> {
            connectToClientInBackground(finalClientIp, finalClientPort, finalRequest);
        }).start();
    }
    
    // connect to TCP client in background thread
    private static void connectToClientInBackground(String clientIp, int clientPort, String request) {
        try (Socket socket = new Socket(clientIp, clientPort)) {
            socket.setSoTimeout(15000);

            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(request.getBytes());
            outputStream.flush();

            InputStream inputStream = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String response = reader.readLine();
            
            if (response != null) {
                Log.i("ConnectToClient", "Received client response: " + response);
            } else {
                Log.i("ConnectToClient", "No response from client");
            }
        } catch (Exception e) {
            Log.e("ConnectToClient", "Failed to connect to client: " + e.getMessage());
        }
    }
    
    // find local ip address in the same subnet as the client
    private static String findServerIpInSameSubnet(String clientIp) {
        try {
            String clientSubnet = getSubnetPrefix(clientIp);

            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    String localIp = address.getHostAddress();
                    
                    if (localIp.contains(":")) {
                        continue;
                    }
                    
                    if (getSubnetPrefix(localIp).equals(clientSubnet)) {
                        return localIp;
                    }
                }
            }
        } catch (SocketException e) {
            State.log("Failed to find matching IP: " + e);
        }
        
        return null;
    }
    
    // get subnet prefix of an IP address, assume class C network, take first three numbers
    private static String getSubnetPrefix(String ip) {
        String[] octets = ip.split("\\.");
        if (octets.length >= 3) {
            return octets[0] + "." + octets[1] + "." + octets[2];
        }
        return "";
    }
}
