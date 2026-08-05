package io.github.jqssun.displaymirror.shizuku;

import android.annotation.TargetApi;
import android.app.ActivityThread;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Process;
import io.github.jqssun.displaymirror.job.AndroidVersions;

/*
user service runs as shell UID, so attribution must claim a package that UID owns
*/
public final class FakeContext extends ContextWrapper {
  private final String packageName;

  public FakeContext(Context base) {
    super(base);
    packageName = _resolvePackageName();
  }

  // first package owned by the UID we run as
  // raw binder, not ServiceUtils: we are already the privileged process
  private static String _resolvePackageName() {
    try {
      String[] packages = ActivityThread.getPackageManager().getPackagesForUid(Process.myUid());
      if (packages != null && packages.length > 0) {
        return packages[0];
      }
    } catch (Throwable e) {
      Ln.e("failed to resolve package for uid " + Process.myUid(), e);
    }
    return null;
  }

  @Override
  public String getPackageName() {
    return packageName;
  }

  @Override
  public String getOpPackageName() {
    return packageName;
  }

  @TargetApi(AndroidVersions.API_31_ANDROID_12)
  @Override
  public AttributionSource getAttributionSource() {
    return new AttributionSource.Builder(Process.myUid()).setPackageName(packageName).build();
  }
}
