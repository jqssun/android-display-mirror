package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.topjohnwu.superuser.Shell;
import io.github.jqssun.displaymirror.airplay.AirPlayService;
import io.github.jqssun.displaymirror.displaylink.ApkImporter;
import io.github.jqssun.displaymirror.displaylink.MirrorDisplaylinkMonitor;
import io.github.jqssun.displaymirror.job.AcquireShizuku;
import io.github.jqssun.displaymirror.job.CaptureAudio;
import io.github.jqssun.displaymirror.job.CreateVirtualDisplay;
import io.github.jqssun.displaymirror.shizuku.ServiceUtils;
import io.github.jqssun.displaymirror.sunshine.SunshineHost;
import org.lsposed.hiddenapibypass.HiddenApiBypass;
import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
  public static final String ACTION_OPEN_OVERVIEW =
      "io.github.jqssun.displaymirror.action.OPEN_OVERVIEW";
  public static final String ACTION_OPEN_SCREEN =
      "io.github.jqssun.displaymirror.action.OPEN_SCREEN";
  public static final String EXTEND_PACKAGE_NAME = "io.github.jqssun.displayextend";
  public static final String ACTION_OPEN_EXTEND_OVERVIEW =
      "io.github.jqssun.displayextend.action.OPEN_OVERVIEW";
  public static final String ACTION_OPEN_EXTEND_DISPLAY_DETAIL =
      "io.github.jqssun.displayextend.action.OPEN_DISPLAY_DETAIL";
  public static final String ACTION_OPEN_EXTEND_SETTINGS =
      "io.github.jqssun.displayextend.action.OPEN_SETTINGS";
  public static final String EXTRA_DISPLAY_ID = "display_id";
  public static final String EXTRA_SCREEN = "screen";
  public static final String EXTRA_SOURCE_SCREEN = "source_screen";
  public static final String SCREEN_OVERVIEW = "overview";
  public static final String SCREEN_SUNSHINE = "sunshine";
  public static final String SCREEN_AIRPLAY = "airplay";
  public static final String SCREEN_DISPLAYLINK = "displaylink";
  public static final String SCREEN_SETTINGS = "settings";
  public static final String SOURCE_EXTEND_OVERVIEW = "extend_overview";
  private static final Uri EXTEND_MARKET_URI =
      Uri.parse("market://details?id=" + EXTEND_PACKAGE_NAME);
  private static final Uri EXTEND_PROJECT_URI =
      Uri.parse("https://github.com/jqssun/android-display-extend");

  static {
    Shell.enableVerboseLogging = BuildConfig.DEBUG;
    Shell.setDefaultBuilder(
        Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).setTimeout(10));
  }

  private NavController navController;
  private BottomNavigationView bottomNav;
  private OnBackPressedCallback crossAppBackCallback;
  private String crossAppLandingScreen;
  private long lastCheckTime = 0;
  private boolean pendingSunshineStart = false;

  private final ActivityResultLauncher<Intent> mediaProjectionLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
              Intent data = result.getData();
              State.log("user granted projection permission");
              lastCheckTime = System.currentTimeMillis();
              if (ProjectionService.instance == null) {
                Intent svc = new Intent(this, ProjectionService.class);
                svc.putExtra("data", data);
                startForegroundService(svc);
                State.log("starting ProjectionService");
              } else {
                MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                if (mpm == null) return;
                State.setMediaProjection(mpm.getMediaProjection(RESULT_OK, data));
                if (State.getMediaProjection() == null) {
                  State.resumeJob();
                  return;
                }
                State.getMediaProjection()
                    .registerCallback(
                        new MediaProjection.Callback() {
                          @Override
                          public void onStop() {
                            super.onStop();
                            State.log("MediaProjection onStop callback");
                          }
                        },
                        null);
                State.resumeJob();
                State.fireProjectionReady();
              }
              if (pendingSunshineStart) {
                pendingSunshineStart = false;
                SunshineHost.start(this);
              }
            } else {
              State.log("user denied projection permission");
              pendingSunshineStart = false;
              State.setProjectionReadyCallback(null);
              refresh();
              State.resumeJob();
            }
          });

  private final ActivityResultLauncher<String> recordAudioLauncher =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          granted -> {
            State.log("audio recording permission " + (granted ? "granted" : "denied"));
            State.resumeJob();
          });

  private final ActivityResultLauncher<Intent> importApkLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
              Uri uri = result.getData().getData();
              if (uri != null) {
                try {
                  String err = ApkImporter.importFromApk(this, uri);
                  if (err == null) {
                    Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show();
                    State.log("DisplayLink APK imported successfully");
                  } else {
                    Toast.makeText(this, getString(R.string.import_failed, err), Toast.LENGTH_LONG)
                        .show();
                    State.log("APK import error: " + err);
                  }
                } catch (Exception e) {
                  Toast.makeText(
                          this,
                          getString(R.string.import_failed, e.getMessage()),
                          Toast.LENGTH_LONG)
                      .show();
                  State.log("APK import exception: " + e.getMessage());
                }
                refresh();
              }
            }
          });

  private void _onRequestShizukuPermissionsResult(int requestCode, int grantResult) {
    if (requestCode == AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE) {
      State.log(
          "Shizuku permission result: "
              + (grantResult == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
      State.resumeJob();
    } else {
      State.log("unknown Shizuku request code: " + requestCode);
    }
  }

  private final Shizuku.OnRequestPermissionResultListener requestPermissionResultListener =
      this::_onRequestShizukuPermissionsResult;

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      try {
        HiddenApiBypass.addHiddenApiExemptions("");
      } catch (Exception e) {
        android.util.Log.e("MainActivity", "Failed to add hidden API exemption: " + e.getMessage());
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    super.onCreate(savedInstanceState);
    State.setCurrentActivity(this);
    getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    Shizuku.addRequestPermissionResultListener(requestPermissionResultListener);
    Shizuku.addBinderReceivedListenerSticky(_binderReceivedListener);
    Shizuku.addBinderDeadListener(_binderDeadListener);

    setContentView(R.layout.activity_main);
    TvFocus.attach(getWindow());

    // setup navigation
    NavHostFragment navHostFragment =
        (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    navController = navHostFragment.getNavController();
    bottomNav = findViewById(R.id.bottom_nav);
    View navHost = findViewById(R.id.nav_host_fragment);
    bottomNav.addOnLayoutChangeListener(
        (v, l, t, r, b, ol, ot, orr, ob) ->
            navHost.setPadding(
                navHost.getPaddingLeft(),
                navHost.getPaddingTop(),
                navHost.getPaddingRight(),
                b - t));
    MaterialToolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    AppBarConfiguration appBarConfig =
        new AppBarConfiguration.Builder(
                R.id.overview_fragment, R.id.logs_fragment, R.id.settings_fragment)
            .build();
    NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig);
    NavigationUI.setupWithNavController(bottomNav, navController);
    crossAppBackCallback =
        new OnBackPressedCallback(false) {
          @Override
          public void handleOnBackPressed() {
            _returnToExtendOverview();
          }
        };
    getOnBackPressedDispatcher().addCallback(this, crossAppBackCallback);
    navController.addOnDestinationChangedListener(
        (controller, destination, arguments) -> _updateCrossAppBackState());
    _handleLaunchIntent(getIntent());

    MirrorDisplaylinkMonitor.init(this);
    CaptureAudio.requestPermission();

    State.uiState.observe(this, state -> {});
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    _handleLaunchIntent(intent);
  }

  @Override
  public boolean onSupportNavigateUp() {
    NavHostFragment navHostFragment =
        (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    return NavigationUI.navigateUp(
            navHostFragment.getNavController(),
            new AppBarConfiguration.Builder(
                    R.id.overview_fragment, R.id.logs_fragment, R.id.settings_fragment)
                .build())
        || super.onSupportNavigateUp();
  }

  @Override
  protected void onResume() {
    super.onResume();
    State.setCurrentActivity(this);
    refresh();
  }

  private final Shizuku.OnBinderReceivedListener _binderReceivedListener =
      () -> {
        State.log("Shizuku binder received");
        // clear forced display size left over from a crashed session
        if (State.mediaProjectionInUse == null) {
          CreateVirtualDisplay.restoreAspectRatio();
        }
      };

  private final Shizuku.OnBinderDeadListener _binderDeadListener =
      () -> {
        State.log("Shizuku binder DIED");
        ServiceUtils.invalidate();
      };

  @Override
  protected void onDestroy() {
    super.onDestroy();
    Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener);
    Shizuku.removeBinderReceivedListener(_binderReceivedListener);
    Shizuku.removeBinderDeadListener(_binderDeadListener);
    // a relaunch already registered the new instance, don't wipe it
    if (State.getCurrentActivity() == this) {
      State.setCurrentActivity(null);
    }
  }

  public void startSunshine() {
    AcquireShizuku.notifyIfUidDropped();
    if (SunshineHost.isRunning()) {
      refresh();
      return;
    }
    if (ProjectionService.instance == null) {
      pendingSunshineStart = true;
      startMediaProjectionService();
    } else {
      SunshineHost.start(this);
      refresh();
    }
  }

  public void requestRecordAudioPermission() {
    runOnUiThread(() -> recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO));
  }

  public void startAirPlayProjection() {
    State.setProjectionReadyCallback(() -> AirPlayService.getInstance().onProjectionReady());
    startMediaProjectionService();
  }

  public void startMediaProjectionService() {
    MediaProjectionManager mpm =
        (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    if (mpm != null) {
      Intent captureIntent;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        captureIntent =
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
      } else {
        captureIntent = mpm.createScreenCaptureIntent();
      }
      mediaProjectionLauncher.launch(captureIntent);
    } else {
      throw new RuntimeException("Failed to get MediaProjectionManager service");
    }
  }

  private void _handleLaunchIntent(Intent intent) {
    if (intent == null) {
      return;
    }

    boolean doNotAutoStartSunshine = intent.getBooleanExtra("DoNotAutoStartSunshine", false);
    if (doNotAutoStartSunshine) {
      Pref.doNotAutoStartSunshine = true;
    }

    String action = intent.getAction();
    String sourceScreen = intent.getStringExtra(EXTRA_SOURCE_SCREEN);
    if (ACTION_OPEN_OVERVIEW.equals(action)) {
      _navigateToOverview();
      if (SOURCE_EXTEND_OVERVIEW.equals(sourceScreen)) {
        crossAppLandingScreen = SCREEN_OVERVIEW;
      } else {
        crossAppLandingScreen = null;
      }
    } else if (ACTION_OPEN_SCREEN.equals(action)) {
      _openMirrorScreen(intent.getStringExtra(EXTRA_SCREEN));
      if (SOURCE_EXTEND_OVERVIEW.equals(sourceScreen)) {
        crossAppLandingScreen = _normalizeMirrorScreen(intent.getStringExtra(EXTRA_SCREEN));
      } else {
        crossAppLandingScreen = null;
      }
    } else {
      crossAppLandingScreen = null;
    }
    _updateCrossAppBackState();
  }

  public void refresh() {
    MirrorUiState current = State.uiState.getValue();
    if (current != null && current.errorStatusText != null) {
      return;
    }
    MirrorUiState newUiState = new MirrorUiState();
    if (SunshineHost.isRunning()) {
      newUiState.stopBtnVisibility = true;
    } else {
      newUiState.startBtnVisibility = true;
    }
    State.uiState.setValue(newUiState);
  }

  public void downloadDisplayLink(MaterialButton downloadBtn) {
    downloadBtn.setEnabled(false);
    downloadBtn.setText(R.string.downloading_displaylink);
    String url = Pref.getDisplaylinkApkUrl();
    new Thread(
            () -> {
              try {
                String err =
                    ApkImporter.downloadAndImport(
                        this,
                        url,
                        hundredths ->
                            runOnUiThread(
                                () ->
                                    downloadBtn.setText(
                                        String.format("%.2f MB", hundredths / 100.0))));
                runOnUiThread(
                    () -> {
                      downloadBtn.setEnabled(true);
                      downloadBtn.setText(R.string.auto_import_displaylink_libs);
                      if (err == null) {
                        Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show();
                        State.log("DisplayLink libraries downloaded and imported successfully");
                      } else {
                        Toast.makeText(
                                this, getString(R.string.import_failed, err), Toast.LENGTH_LONG)
                            .show();
                        State.log("download import error: " + err);
                      }
                      refresh();
                    });
              } catch (Exception e) {
                runOnUiThread(
                    () -> {
                      downloadBtn.setEnabled(true);
                      downloadBtn.setText(R.string.auto_import_displaylink_libs);
                      Toast.makeText(
                              this,
                              getString(R.string.import_failed, e.getMessage()),
                              Toast.LENGTH_LONG)
                          .show();
                      State.log("download exception: " + e.getMessage());
                    });
              }
            })
        .start();
  }

  public void importApk() {
    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    pick.setType("application/vnd.android.package-archive");
    pick.addCategory(Intent.CATEGORY_OPENABLE);
    importApkLauncher.launch(pick);
  }

  public void manageDisplayInExtend(int displayId, String sourceScreen) {
    if (displayId < 0) {
      return;
    }

    Intent intent = new Intent(ACTION_OPEN_EXTEND_DISPLAY_DETAIL);
    intent.setPackage(EXTEND_PACKAGE_NAME);
    intent.addCategory(Intent.CATEGORY_DEFAULT);
    intent.putExtra(EXTRA_DISPLAY_ID, displayId);
    intent.putExtra(EXTRA_SOURCE_SCREEN, sourceScreen);
    _startExtendIntentOrFallback(intent);
  }

  public void openExtendSettings() {
    Intent intent = new Intent(ACTION_OPEN_EXTEND_SETTINGS);
    intent.setPackage(EXTEND_PACKAGE_NAME);
    intent.addCategory(Intent.CATEGORY_DEFAULT);
    intent.putExtra(EXTRA_SOURCE_SCREEN, SCREEN_SETTINGS);
    _startExtendIntentOrFallback(intent);
  }

  private void _navigateToOverview() {
    if (bottomNav != null && bottomNav.getSelectedItemId() != R.id.overview_fragment) {
      bottomNav.setSelectedItemId(R.id.overview_fragment);
    } else if (navController != null
        && navController.getCurrentDestination() != null
        && navController.getCurrentDestination().getId() != R.id.overview_fragment) {
      navController.popBackStack(R.id.overview_fragment, false);
    }
  }

  private void _navigateToSettings() {
    if (bottomNav != null && bottomNav.getSelectedItemId() != R.id.settings_fragment) {
      bottomNav.setSelectedItemId(R.id.settings_fragment);
    } else if (navController != null
        && navController.getCurrentDestination() != null
        && navController.getCurrentDestination().getId() != R.id.settings_fragment) {
      navController.popBackStack(R.id.settings_fragment, false);
    }
  }

  private void _openMirrorScreen(String screen) {
    String normalizedScreen = _normalizeMirrorScreen(screen);
    if (SCREEN_OVERVIEW.equals(normalizedScreen)) {
      _navigateToOverview();
      return;
    }
    if (SCREEN_SETTINGS.equals(normalizedScreen)) {
      _navigateToSettings();
      return;
    }
    if (_isOnMirrorScreen(normalizedScreen)) {
      return;
    }

    _navigateToOverview();
    if (navController == null) {
      return;
    }

    int destinationId = _getMirrorDestinationId(normalizedScreen);
    if (destinationId != -1 && !_isCurrentDestination(destinationId)) {
      navController.navigate(destinationId);
    }
  }

  private String _normalizeMirrorScreen(String screen) {
    if (SCREEN_SUNSHINE.equals(screen)
        || SCREEN_AIRPLAY.equals(screen)
        || SCREEN_DISPLAYLINK.equals(screen)
        || SCREEN_SETTINGS.equals(screen)) {
      return screen;
    }
    return SCREEN_OVERVIEW;
  }

  private int _getMirrorDestinationId(String screen) {
    switch (_normalizeMirrorScreen(screen)) {
      case SCREEN_SUNSHINE:
        return R.id.sunshine_fragment;
      case SCREEN_AIRPLAY:
        return R.id.airplay_fragment;
      case SCREEN_DISPLAYLINK:
        return R.id.displaylink_fragment;
      case SCREEN_SETTINGS:
        return R.id.settings_fragment;
      case SCREEN_OVERVIEW:
        return R.id.overview_fragment;
      default:
        return -1;
    }
  }

  private boolean _isOnMirrorScreen(String screen) {
    return _isCurrentDestination(_getMirrorDestinationId(screen));
  }

  private boolean _isCurrentDestination(int destinationId) {
    return navController != null
        && navController.getCurrentDestination() != null
        && navController.getCurrentDestination().getId() == destinationId;
  }

  private void _updateCrossAppBackState() {
    if (crossAppBackCallback == null) {
      return;
    }
    crossAppBackCallback.setEnabled(
        crossAppLandingScreen != null && _isOnMirrorScreen(crossAppLandingScreen));
  }

  private void _returnToExtendOverview() {
    Intent intent = new Intent(ACTION_OPEN_EXTEND_OVERVIEW);
    intent.setPackage(EXTEND_PACKAGE_NAME);
    intent.addCategory(Intent.CATEGORY_DEFAULT);

    crossAppLandingScreen = null;
    _updateCrossAppBackState();

    if (intent.resolveActivity(getPackageManager()) != null) {
      startActivity(intent);
    }
    moveTaskToBack(true);
  }

  private void _startExtendIntentOrFallback(Intent intent) {
    if (intent.resolveActivity(getPackageManager()) == null) {
      Toast.makeText(this, R.string.extend_app_not_installed, Toast.LENGTH_SHORT).show();
      Intent market = new Intent(Intent.ACTION_VIEW, EXTEND_MARKET_URI);
      if (market.resolveActivity(getPackageManager()) != null) {
        startActivity(market);
      } else {
        startActivity(new Intent(Intent.ACTION_VIEW, EXTEND_PROJECT_URI));
      }
      return;
    }
    startActivity(intent);
  }

  public String getCurrentScreen() {
    if (navController == null || navController.getCurrentDestination() == null) {
      return SCREEN_OVERVIEW;
    }
    int destinationId = navController.getCurrentDestination().getId();
    if (destinationId == R.id.sunshine_fragment) {
      return SCREEN_SUNSHINE;
    }
    if (destinationId == R.id.airplay_fragment) {
      return SCREEN_AIRPLAY;
    }
    if (destinationId == R.id.displaylink_fragment) {
      return SCREEN_DISPLAYLINK;
    }
    if (destinationId == R.id.settings_fragment) {
      return SCREEN_SETTINGS;
    }
    return SCREEN_OVERVIEW;
  }
}
