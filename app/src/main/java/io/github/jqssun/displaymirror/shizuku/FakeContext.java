package io.github.jqssun.displaymirror.shizuku;

import android.annotation.TargetApi;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Process;
import io.github.jqssun.displaymirror.job.AndroidVersions;

/*
user service runs as shell UID, so attribution must claim a package that UID owns
*/
public final class FakeContext extends ContextWrapper {
  private static final String PACKAGE_NAME = "com.android.shell";

  public FakeContext(Context base) {
    super(base);
  }

  @Override
  public String getPackageName() {
    return PACKAGE_NAME;
  }

  @Override
  public String getOpPackageName() {
    return PACKAGE_NAME;
  }

  @TargetApi(AndroidVersions.API_31_ANDROID_12)
  @Override
  public AttributionSource getAttributionSource() {
    return new AttributionSource.Builder(Process.myUid()).setPackageName(PACKAGE_NAME).build();
  }
}
