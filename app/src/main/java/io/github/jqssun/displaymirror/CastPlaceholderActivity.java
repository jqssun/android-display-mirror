package io.github.jqssun.displaymirror;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

// shown on an own-content cast display while empty, so the user knows to pick an app
public class CastPlaceholderActivity extends AppCompatActivity {

  private static CastPlaceholderActivity instance;

  public static void launchOnDisplay(Context context, int displayId) {
    Intent intent = new Intent(context, CastPlaceholderActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    ActivityOptions options = ActivityOptions.makeCustomAnimation(context, 0, 0);
    options.setLaunchDisplayId(displayId);
    context.getApplicationContext().startActivity(intent, options.toBundle());
  }

  // finish before its display is released, else it relocates to the main screen
  public static void dismiss() {
    CastPlaceholderActivity a = instance;
    if (a != null) {
      instance = null;
      a.runOnUiThread(a::finish);
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    instance = this;
    setContentView(R.layout.activity_cast_placeholder);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (instance == this) {
      instance = null;
    }
  }
}
