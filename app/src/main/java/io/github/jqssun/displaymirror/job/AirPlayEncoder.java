package io.github.jqssun.displaymirror.job;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.nio.ByteBuffer;

import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;

public class AirPlayEncoder {
    private static final String TAG = "AirPlayEncoder";
    private MediaCodec codec;
    private VirtualDisplay virtualDisplay;
    private volatile boolean running;
    private Thread encodeThread;
    private byte[] codecConfig;

    public int screenWidth;
    public int screenHeight;
    public int screenDpi;

    public void start(MediaProjection projection, int fps) {
        if (running) return;
        running = true;

        try {
            // Get actual screen dimensions
            Context ctx = State.getContext();
            if (ctx == null) {
                running = false;
                return;
            }
            DisplayMetrics dm = new DisplayMetrics();
            ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRealMetrics(dm);
            screenWidth = dm.widthPixels;
            screenHeight = dm.heightPixels;
            screenDpi = dm.densityDpi;

            if (Pref.getAirPlay1Mode()) {
                // Portrait input buffers segfault the fallback software
                // decoder and 1080p IDRs overflow its 2 MiB input ceiling
                // so swap to landscape and cap at 1280x720
                if (screenHeight > screenWidth) {
                    int tmp = screenWidth;
                    screenWidth = screenHeight;
                    screenHeight = tmp;
                }
                if (screenWidth > 1280) {
                    screenHeight = (int) ((long) screenHeight * 1280L / screenWidth);
                    screenWidth = 1280;
                }
                if (screenHeight > 720) {
                    screenWidth = (int) ((long) screenWidth * 720L / screenHeight);
                    screenHeight = 720;
                }
                screenWidth &= ~1;
                screenHeight &= ~1;
            }

            // Android 14+ requires a callback registered before createVirtualDisplay
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    State.log("AirPlay MediaProjection stopped");
                    stop();
                }
            }, new android.os.Handler(android.os.Looper.getMainLooper()));

            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, screenWidth, screenHeight);
            boolean airplay1 = Pref.getAirPlay1Mode();
            format.setInteger(MediaFormat.KEY_BIT_RATE, airplay1 ? 4_000_000 : 8_000_000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, airplay1 ? 1 : 3);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            }
            format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0);
            if (airplay1) {
                // Baseline@4.0 is the only profile/level combo that clears
                // every known third-party decoder quirk. See AIRPIN.md.
                format.setInteger(MediaFormat.KEY_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
                format.setInteger(MediaFormat.KEY_LEVEL,
                    MediaCodecInfo.CodecProfileLevel.AVCLevel4);
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface inputSurface = codec.createInputSurface();
            codec.start();

            virtualDisplay = projection.createVirtualDisplay(
                "AirPlayMirror", screenWidth, screenHeight, screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                inputSurface, null, null
            );
            State.setAirPlayTouchTarget(virtualDisplay.getDisplay().getDisplayId(), inputSurface);

            encodeThread = new Thread(this::_encodeLoop, "AirPlayEncode");
            encodeThread.start();
            State.log("AirPlay encoder: " + screenWidth + "x" + screenHeight + " dpi=" + screenDpi + " fps=" + fps);
        } catch (Exception e) {
            Log.e(TAG, "start failed", e);
            State.log("AirPlay encoder failed: " + e.getMessage());
            State.clearAirPlayTouchTarget();
            running = false;
        }
    }

    public void stop() {
        running = false;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        State.clearAirPlayTouchTarget();
        if (codec != null) {
            try { codec.stop(); } catch (Exception ignored) {}
            try { codec.release(); } catch (Exception ignored) {}
            codec = null;
        }
        if (encodeThread != null) {
            encodeThread.interrupt();
            encodeThread = null;
        }
        codecConfig = null;
    }

    private void _encodeLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (running) {
            try {
                int idx = codec.dequeueOutputBuffer(info, 100_000);
                if (idx < 0) continue;

                ByteBuffer buf = codec.getOutputBuffer(idx);
                if (buf == null) {
                    codec.releaseOutputBuffer(idx, false);
                    continue;
                }

                byte[] data = new byte[info.size];
                buf.position(info.offset);
                buf.get(data, 0, info.size);

                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    codecConfig = data;
                } else {
                    boolean isKey = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                    if (isKey && codecConfig != null) {
                        byte[] combined = new byte[codecConfig.length + data.length];
                        System.arraycopy(codecConfig, 0, combined, 0, codecConfig.length);
                        System.arraycopy(data, 0, combined, codecConfig.length, data.length);
                        AirPlayService.onNativeVideoFrame(combined, true);
                    } else {
                        AirPlayService.onNativeVideoFrame(data, isKey);
                    }
                }

                codec.releaseOutputBuffer(idx, false);
            } catch (Exception e) {
                if (running) Log.e(TAG, "encode error", e);
            }
        }
    }
}
