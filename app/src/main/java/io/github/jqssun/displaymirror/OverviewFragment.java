package io.github.jqssun.displaymirror;

import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import io.github.jqssun.displaymirror.job.AutoRotateAndScaleForDisplaylink;
import io.github.jqssun.displaymirror.job.CreateVirtualDisplay;
import io.github.jqssun.displaymirror.job.ExitAll;
import io.github.jqssun.displaymirror.job.FetchLogAndShare;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

public class OverviewFragment extends Fragment {

    private MaterialButton startBtn, stopBtn, screenOffBtn, touchScreenBtn;
    private TextView mirrorStatus;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_overview, container, false);

        startBtn = view.findViewById(R.id.startBtn);
        stopBtn = view.findViewById(R.id.stopBtn);
        screenOffBtn = view.findViewById(R.id.screenOffBtn);
        touchScreenBtn = view.findViewById(R.id.touchScreenBtn);
        mirrorStatus = view.findViewById(R.id.mirrorStatus);

        startBtn.setOnClickListener(v -> ((MirrorMainActivity) requireActivity()).startMirroring());
        stopBtn.setOnClickListener(v -> {
            if (AutoRotateAndScaleForDisplaylink.instance != null) {
                AutoRotateAndScaleForDisplaylink.instance.release();
            }
            ExitAll.execute(requireActivity(), false);
        });
        screenOffBtn.setOnClickListener(v -> CreateVirtualDisplay.doPowerOffScreen(requireActivity()));
        touchScreenBtn.setOnClickListener(v -> _onTouchScreenClick());

        // About
        TextView versionText = view.findViewById(R.id.versionText);
        try {
            String ver = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            versionText.setText(getString(R.string.version_format, ver, android.os.Build.VERSION.RELEASE));
        } catch (Exception e) {
            versionText.setText(R.string.version_unknown);
        }
        view.findViewById(R.id.websiteLink).setOnClickListener(v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jqssun/android-screen-mirror"))));

        // Double-tap about card to export logs
        View aboutCard = view.findViewById(R.id.aboutCard);
        GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (ShizukuUtils.hasPermission()) {
                    State.startNewJob(new FetchLogAndShare(getContext()));
                }
                return true;
            }
            @Override
            public boolean onDown(MotionEvent e) { return true; }
        });
        aboutCard.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        // Shizuku link
        view.findViewById(R.id.shizukuBtn).setOnClickListener(v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rikkaapps/shizuku"))));

        // Exit
        view.findViewById(R.id.exitBtn).setOnClickListener(v -> {
            if (AutoRotateAndScaleForDisplaylink.instance != null) {
                AutoRotateAndScaleForDisplaylink.instance.release();
            }
            ExitAll.execute(requireActivity(), false);
        });

        State.uiState.observe(getViewLifecycleOwner(), this::_updateUI);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ((MirrorMainActivity) requireActivity()).refresh();
    }

    private void _onTouchScreenClick() {
        boolean useTouchscreen = Pref.getUseTouchscreen();
        if (ShizukuUtils.hasPermission() && useTouchscreen) {
            VirtualDisplay virtualDisplay = State.displaylinkState.getVirtualDisplay();
            if (virtualDisplay == null) virtualDisplay = State.mirrorVirtualDisplay;
            if (virtualDisplay == null) return;
            int displayId = virtualDisplay.getDisplay().getDisplayId();
            Intent intent = new Intent(requireContext(), TouchscreenActivity.class);
            intent.putExtra("surface", virtualDisplay.getSurface());
            intent.putExtra("display", displayId);
            startActivity(intent);
        } else {
            TouchpadActivity.startTouchpad(requireActivity(), State.lastSingleAppDisplay, false);
        }
    }

    private void _updateUI(MirrorUiState state) {
        if (state.errorStatusText != null) {
            mirrorStatus.setText(state.errorStatusText);
            startBtn.setVisibility(View.GONE);
            stopBtn.setVisibility(View.GONE);
            screenOffBtn.setVisibility(View.GONE);
            touchScreenBtn.setVisibility(View.GONE);
            return;
        }
        mirrorStatus.setText(state.mirrorStatusText);

        startBtn.setVisibility(state.startBtnVisibility ? View.VISIBLE : View.GONE);
        stopBtn.setVisibility(state.stopBtnVisibility ? View.VISIBLE : View.GONE);
        screenOffBtn.setVisibility(state.screenOffBtnVisibility ? View.VISIBLE : View.GONE);
        touchScreenBtn.setVisibility(state.touchScreenBtnVisibility ? View.VISIBLE : View.GONE);

        if (state.touchScreenBtnVisibility) {
            touchScreenBtn.setText(state.touchScreenBtnText);
        }
    }
}
