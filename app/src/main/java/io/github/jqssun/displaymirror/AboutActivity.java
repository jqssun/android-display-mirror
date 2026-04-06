package io.github.jqssun.displaymirror;

import static io.github.jqssun.displaymirror.job.AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import io.github.jqssun.displaymirror.job.FetchLogAndShare;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

import rikka.shizuku.Shizuku;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        
        TextView aboutContent = findViewById(R.id.aboutContent);
        aboutContent.setText(R.string.about_legal_notice);

        TextView websiteLink = findViewById(R.id.websiteLink);
        websiteLink.setOnClickListener(v -> openUrl("https://github.com/jqssun/android-screen-mirror"));

        TextView versionText = findViewById(R.id.versionText);
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            String androidVersion = android.os.Build.VERSION.RELEASE;
            versionText.setText(getString(R.string.version_format, versionName, androidVersion));
        } catch (Exception e) {
            versionText.setText(R.string.version_unknown);
        }

        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!ShizukuUtils.hasShizukuStarted()) {
                    State.log("shizuku not started");
                    return false;
                }
                if (!ShizukuUtils.hasPermission()) {
                    State.log("ask shizuku permission");
                    Toast.makeText(AboutActivity.this, R.string.export_log_needs_shizuku, Toast.LENGTH_SHORT).show();
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
                    return false;
                }
                State.startNewJob(new FetchLogAndShare(AboutActivity.this));
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        View header = findViewById(R.id.header);
        header.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

} 