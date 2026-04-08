package io.github.jqssun.displaymirror;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ApkImporter {

    private static final Set<String> REQUIRED_LIBS = new HashSet<>(Arrays.asList(
            "libDisplayLinkManager.so", "libusb_android.so", "libAndroidDLM.so"
    ));

    static File _dir(Context ctx) {
        return new File(ctx.getFilesDir(), "DisplayLink");
    }

    public static File jniLibDir(Context ctx) {
        String abi = Build.SUPPORTED_ABIS[0];
        return new File(_dir(ctx), "jniLibs/" + abi);
    }

    public static File assetsDir(Context ctx) {
        return new File(_dir(ctx), "assets");
    }

    public static boolean areLibsImported(Context ctx) {
        File libDir = jniLibDir(ctx);
        for (String lib : REQUIRED_LIBS) {
            File f = new File(libDir, lib);
            if (!f.exists() || f.length() == 0) return false;
        }
        return true;
    }

    public static boolean areFirmwaresImported(Context ctx) {
        File dir = assetsDir(ctx);
        if (!dir.exists()) return false;
        String[] files = dir.list();
        if (files == null) return false;
        for (String f : files) {
            if (f.endsWith(".spkg")) return true;
        }
        return false;
    }

    public static String downloadAndImport(Context ctx, String url, java.util.function.IntConsumer onProgress) throws IOException {
        File tmp = new File(_dir(ctx), "tmp.apk");
        _dir(ctx).mkdirs();
        try {
            HttpURLConnection conn = _openFollowingRedirects(url);
            if (conn.getResponseCode() != 200) {
                return "Download failed: HTTP " + conn.getResponseCode();
            }
            long downloaded = 0;
            int lastMb = -1;
            try (InputStream is = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                    downloaded += n;
                    if (onProgress != null) {
                        int mb = (int)(downloaded / (1024 * 1024));
                        if (mb != lastMb) {
                            lastMb = mb;
                            onProgress.accept(mb);
                        }
                    }
                }
            }
            return importFromFile(ctx, tmp);
        } finally {
            tmp.delete();
        }
    }

    private static HttpURLConnection _openFollowingRedirects(String url) throws IOException {
        int maxRedirects = 10;
        for (int i = 0; i < maxRedirects; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.connect();
            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null) break;
                url = location;
                continue;
            }
            return conn;
        }
        throw new IOException("Too many redirects");
    }

    public static String importFromFile(Context ctx, File apk) throws IOException {
        try (FileInputStream fis = new FileInputStream(apk)) {
            return _importFromStream(ctx, fis);
        }
    }

    public static String importFromApk(Context ctx, Uri uri) throws IOException {
        InputStream is = ctx.getContentResolver().openInputStream(uri);
        if (is == null) return "Failed to open file";
        try (is) {
            return _importFromStream(ctx, is);
        }
    }

    private static String _importFromStream(Context ctx, InputStream is) throws IOException {
        File libDir = jniLibDir(ctx);
        File fwDir = assetsDir(ctx);
        libDir.mkdirs();
        fwDir.mkdirs();

        String abi = Build.SUPPORTED_ABIS[0];
        String libPrefix = "lib/" + abi + "/";

        int libCount = 0;
        int fwCount = 0;

        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.startsWith(libPrefix) && name.endsWith(".so")) {
                    String soName = name.substring(name.lastIndexOf('/') + 1);
                    if (REQUIRED_LIBS.contains(soName)) {
                        _extractTo(zis, new File(libDir, soName));
                        libCount++;
                    }
                } else if (name.startsWith("assets/") && name.endsWith(".spkg")) {
                    String fwName = name.substring(name.lastIndexOf('/') + 1);
                    _extractTo(zis, new File(fwDir, fwName));
                    fwCount++;
                }

                zis.closeEntry();
            }
        }

        if (libCount < REQUIRED_LIBS.size()) {
            return "APK missing native libraries for " + abi + " (found " + libCount + "/" + REQUIRED_LIBS.size() + ")";
        }
        if (fwCount == 0) {
            return "APK missing firmware files (.spkg)";
        }
        return null;
    }

    private static void _extractTo(ZipInputStream zis, File out) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = zis.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
        }
    }
}
