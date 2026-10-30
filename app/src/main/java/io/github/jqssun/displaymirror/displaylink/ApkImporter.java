package io.github.jqssun.displaymirror.displaylink;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import io.github.jqssun.displaymirror.R;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkImporter {

  public static final String PRESENTER_PACKAGE = "com.displaylink.presenter";

  private static final Set<String> REQUIRED_LIBS =
      new HashSet<>(
          Arrays.asList("libDisplayLinkManager.so", "libusb_android.so", "libAndroidDLM.so"));

  static File _dir(Context ctx) {
    return new File(ctx.getFilesDir(), "DisplayLink");
  }

  public static File jniLibDir(Context ctx) {
    String abi = Build.SUPPORTED_ABIS[0];
    return new File(_dir(ctx), "jniLibs/" + abi);
  }

  public static boolean areLibsImported(Context ctx) {
    File libDir = jniLibDir(ctx);
    for (String lib : REQUIRED_LIBS) {
      File f = new File(libDir, lib);
      if (!f.exists() || f.length() == 0) return false;
    }
    return true;
  }

  public static String importFromPresenter(Context ctx) throws IOException {
    PackageManager pm = ctx.getPackageManager();
    ApplicationInfo info;
    try {
      info = pm.getApplicationInfo(PRESENTER_PACKAGE, 0);
    } catch (PackageManager.NameNotFoundException e) {
      return ctx.getString(R.string.presenter_not_installed);
    }

    List<String> apks = new ArrayList<>(Collections.singletonList(info.sourceDir));
    if (info.splitSourceDirs != null) apks.addAll(Arrays.asList(info.splitSourceDirs));

    File libDir = jniLibDir(ctx);
    libDir.mkdirs();
    String abi = Build.SUPPORTED_ABIS[0];
    String libPrefix = "lib/" + abi + "/";
    Set<String> libs = new HashSet<>();
    int fwCount = 0;

    for (String apk : apks) {
      try (ZipFile zip = new ZipFile(apk)) {
        for (ZipEntry entry : Collections.list(zip.entries())) {
          String name = entry.getName();
          String file = name.substring(name.lastIndexOf('/') + 1);
          if (name.startsWith(libPrefix) && REQUIRED_LIBS.contains(file)) {
            _extract(zip, entry, new File(libDir, file));
            libs.add(file);
          } else if (name.startsWith("assets/") && file.endsWith(".spkg")) {
            _extract(zip, entry, new File(ctx.getFilesDir(), file));
            fwCount++;
          }
        }
      }
    }

    if (libs.size() < REQUIRED_LIBS.size()) {
      return ctx.getString(R.string.presenter_missing_libs, abi);
    }
    if (fwCount == 0) return ctx.getString(R.string.presenter_missing_firmware);
    return null;
  }

  private static void _extract(ZipFile zip, ZipEntry entry, File out) throws IOException {
    try (InputStream in = zip.getInputStream(entry)) {
      Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
