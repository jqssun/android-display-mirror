package io.github.jqssun.displaymirror.airplay;

import android.media.projection.MediaProjection;
import android.os.Process;
import android.util.Log;
import io.github.jqssun.displaymirror.State;
import io.github.jqssun.displaymirror.job.CaptureAudio;
import java.util.Arrays;

// RAOP mirroring audio fixed at 44.1kHz stereo S16LE; Go side frames it into ALAC
public class AirPlayAudio {
  private static final String TAG = "AirPlayAudio";
  private static final int SAMPLE_RATE = 44100;
  // exactly one ALAC frame (352 samples) per read
  // batching bursts the send side and Apple drops frames late against the sync anchor
  private static final int READ_SIZE = 352 * 2 * 2;

  private volatile CaptureAudio.PcmStream stream;
  private volatile boolean running;
  private Thread readThread;

  // open can wait for Shizuku bind, so it runs on the read thread
  public void start(MediaProjection projection) {
    if (running) {
      return;
    }
    running = true;
    readThread = new Thread(() -> _run(projection), "AirPlayAudio");
    readThread.start();
  }

  public void stop() {
    running = false;
    _release();
    if (readThread != null) {
      readThread.interrupt();
      readThread = null;
    }
  }

  // idempotent: stop() and the read thread both release
  private synchronized void _release() {
    if (stream != null) {
      stream.close();
      stream = null;
    }
  }

  private void _run(MediaProjection projection) {
    // drain has to keep up with capture or samples are lost
    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
    CaptureAudio.PcmStream s = CaptureAudio.openPcm16(projection, SAMPLE_RATE);
    if (s == null) {
      running = false;
      return;
    }
    synchronized (this) {
      if (!running) {
        // stopped while open was waiting
        s.close();
        return;
      }
      stream = s;
    }
    State.log("AirPlay audio: streaming 44.1kHz stereo");
    try {
      _readLoop(s);
    } finally {
      running = false;
      _release();
    }
  }

  private void _readLoop(CaptureAudio.PcmStream s) {
    byte[] buf = new byte[READ_SIZE];
    while (running) {
      try {
        int n = s.read(buf);
        if (n > 0) {
          AirPlayService.onAudioFrame(n == buf.length ? buf : Arrays.copyOf(buf, n));
        } else {
          // 0 means recorder stopped; < 0 means error
          if (running) State.log("AirPlay audio stopped: read failed (" + n + ")");
          return;
        }
      } catch (Exception e) {
        if (running) {
          Log.e(TAG, "read error", e);
          State.log("AirPlay audio stopped: " + e);
        }
        return;
      }
    }
  }
}
