package io.github.jqssun.displaymirror.job;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import io.github.jqssun.displaymirror.State;

// shared gl pipeline between a virtual display and a stream output
// output is either an encoder surface or a frame sink reading the bound framebuffer
public class StreamRenderer {
  private static final String TAG = "StreamRenderer";

  public interface DisplayFactory {
    // runs on the render thread once input surfaces exist; returns the display to drive or null
    VirtualDisplay create(Surface input, int width, int height);
  }

  public interface FrameSink {
    // runs on the render thread with the frame drawn into the bound framebuffer
    void postFrame();
  }

  private final VirtualDisplayArgs args;
  private final boolean rotate;
  private final boolean crop;
  private final Surface output;
  private final FrameSink sink;

  private HandlerThread ownThread;
  private Handler handler;
  private DisplayManager displayManager;
  private VirtualDisplay display;
  private int displayId = -1;
  private volatile boolean stopped;

  private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
  private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
  private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
  private final int[] fbo = new int[1];
  private final int[] fboTexture = new int[1];

  private Renderer portraitRenderer;
  private Renderer landscapeRenderer;
  private SurfaceTexture portraitTexture;
  private SurfaceTexture landscapeTexture;
  private Surface portraitSurface;
  private Surface landscapeSurface;
  private Surface currentSurface;
  private LandscapeAutoScaler scaler;

  public StreamRenderer(VirtualDisplayArgs args, boolean rotate, boolean crop, Surface output) {
    this.args = args;
    this.rotate = rotate;
    this.crop = crop;
    this.output = output;
    this.sink = null;
  }

  public StreamRenderer(
      VirtualDisplayArgs args, boolean rotate, boolean crop, FrameSink sink, Handler handler) {
    this.args = args;
    this.rotate = rotate;
    this.crop = crop;
    this.output = null;
    this.sink = sink;
    this.handler = handler;
  }

  public void start(DisplayFactory factory) {
    Context context = State.getContext();
    if (context == null) {
      return;
    }
    displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    if (handler == null) {
      ownThread = new HandlerThread("StreamRenderThread");
      ownThread.start();
      handler = new Handler(ownThread.getLooper());
    }
    displayManager.registerDisplayListener(displayListener, handler);
    handler.post(() -> _setup(factory));
    State.log(args.virtualDisplayName + " renderer: rotate=" + rotate + ", crop=" + crop);
  }

  public void stop() {
    if (stopped) {
      return;
    }
    stopped = true;
    if (displayManager != null) {
      displayManager.unregisterDisplayListener(displayListener);
    }
    if (handler != null) {
      handler.post(this::_releaseGl);
    }
    if (ownThread != null) {
      ownThread.quitSafely();
      ownThread = null;
    }
  }

  public void exitCrop() {
    if (handler == null) {
      return;
    }
    handler.post(
        () -> {
          if (scaler != null) {
            scaler.exitScale();
          }
        });
  }

  private final DisplayManager.DisplayListener displayListener =
      new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int id) {}

        @Override
        public void onDisplayRemoved(int id) {
          if (id == displayId) {
            State.log(args.virtualDisplayName + " display removed, stopping renderer");
            stop();
          }
        }

        @Override
        public void onDisplayChanged(int id) {
          if (id == Display.DEFAULT_DISPLAY && rotate) {
            _updateSurface();
            handler.postDelayed(StreamRenderer.this::_updateSurface, 1000);
          }
        }
      };

  private void _setup(DisplayFactory factory) {
    if (stopped) {
      return;
    }
    eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
      throw new RuntimeException("Failed to get EGL display");
    }
    int[] version = new int[2];
    if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
      throw new RuntimeException("Failed to initialize EGL");
    }
    int[] configAttribs = {
      EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
      EGL14.EGL_RED_SIZE, 8,
      EGL14.EGL_GREEN_SIZE, 8,
      EGL14.EGL_BLUE_SIZE, 8,
      EGL14.EGL_ALPHA_SIZE, 8,
      EGL14.EGL_NONE
    };
    EGLConfig[] configs = new EGLConfig[1];
    int[] numConfigs = new int[1];
    EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0);
    int[] contextAttribs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
    eglContext =
        EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
    eglSurface =
        output != null
            ? EGL14.eglCreateWindowSurface(eglDisplay, configs[0], output, null, 0)
            : EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], new int[] {EGL14.EGL_NONE}, 0);
    if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
      throw new RuntimeException("Failed to set EGL context as current");
    }
    GLES20.glViewport(0, 0, args.width, args.height);

    // sink mode renders into the fbo permanently; surface mode uses it for bar detection only
    _createFbo();
    if (sink != null) {
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
    }

    int[] textures = new int[2];
    GLES20.glGenTextures(2, textures, 0);
    _configureInputTexture(textures[0]);
    _configureInputTexture(textures[1]);

    // readpixels output is vertically flipped, compensated by flipped texture coords and a
    // mirrored portrait rotation
    float[] portraitMatrix = new float[16];
    android.opengl.Matrix.setRotateM(portraitMatrix, 0, sink != null ? 270 : 90, 0, 0, 1.0f);
    portraitRenderer = new Renderer(textures[0], portraitMatrix);
    landscapeRenderer = new Renderer(textures[1], null);
    scaler =
        new LandscapeAutoScaler(
            landscapeRenderer.texRenderer, args.width, args.height, sink != null ? 0 : fbo[0]);

    portraitTexture = new SurfaceTexture(textures[0]);
    portraitTexture.setDefaultBufferSize(args.height, args.width);
    portraitTexture.setOnFrameAvailableListener(portraitRenderer);
    portraitSurface = new Surface(portraitTexture);

    landscapeTexture = new SurfaceTexture(textures[1]);
    landscapeTexture.setDefaultBufferSize(args.width, args.height);
    landscapeTexture.setOnFrameAvailableListener(landscapeRenderer);
    landscapeSurface = new Surface(landscapeTexture);

    boolean isLandscape = !rotate || _isDefaultDisplayLandscape();
    currentSurface = isLandscape ? landscapeSurface : portraitSurface;
    display =
        factory.create(
            currentSurface,
            isLandscape ? args.width : args.height,
            isLandscape ? args.height : args.width);
    if (display == null) {
      State.log(args.virtualDisplayName + ": no display to render, stopping renderer");
      stop();
      return;
    }
    displayId = display.getDisplay().getDisplayId();
  }

  private void _updateSurface() {
    if (stopped || display == null) {
      return;
    }
    boolean isLandscape = _isDefaultDisplayLandscape();
    Surface next = isLandscape ? landscapeSurface : portraitSurface;
    if (next == currentSurface) {
      return;
    }
    currentSurface = next;
    display.resize(
        isLandscape ? args.width : args.height, isLandscape ? args.height : args.width, args.dpi);
    display.setSurface(currentSurface);
  }

  private boolean _isDefaultDisplayLandscape() {
    DisplayMetrics metrics = new DisplayMetrics();
    displayManager.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics);
    return metrics.widthPixels > metrics.heightPixels;
  }

  private void _createFbo() {
    GLES20.glGenTextures(1, fboTexture, 0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexture[0]);
    GLES20.glTexImage2D(
        GLES20.GL_TEXTURE_2D,
        0,
        GLES20.GL_RGBA,
        args.width,
        args.height,
        0,
        GLES20.GL_RGBA,
        GLES20.GL_UNSIGNED_BYTE,
        null);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glGenFramebuffers(1, fbo, 0);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
    GLES20.glFramebufferTexture2D(
        GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTexture[0], 0);
    int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
    if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
      Log.e(TAG, "FBO creation failed, status: " + status);
    }
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
  }

  private void _configureInputTexture(int textureId) {
    GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId);
    GLES20.glTexParameterf(
        GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
    GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(
        GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(
        GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
  }

  private void _releaseGl() {
    if (portraitSurface != null) {
      portraitSurface.release();
      portraitSurface = null;
    }
    if (portraitTexture != null) {
      portraitTexture.release();
      portraitTexture = null;
    }
    if (landscapeSurface != null) {
      landscapeSurface.release();
      landscapeSurface = null;
    }
    if (landscapeTexture != null) {
      landscapeTexture.release();
      landscapeTexture = null;
    }
    if (portraitRenderer != null) {
      portraitRenderer.texRenderer.release();
      portraitRenderer = null;
    }
    if (landscapeRenderer != null) {
      landscapeRenderer.texRenderer.release();
      landscapeRenderer = null;
    }
    if (fbo[0] != 0) {
      GLES20.glDeleteFramebuffers(1, fbo, 0);
      fbo[0] = 0;
    }
    if (fboTexture[0] != 0) {
      GLES20.glDeleteTextures(1, fboTexture, 0);
      fboTexture[0] = 0;
    }
    if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
      EGL14.eglMakeCurrent(
          eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
      if (eglSurface != EGL14.EGL_NO_SURFACE) {
        EGL14.eglDestroySurface(eglDisplay, eglSurface);
      }
      if (eglContext != EGL14.EGL_NO_CONTEXT) {
        EGL14.eglDestroyContext(eglDisplay, eglContext);
      }
      EGL14.eglTerminate(eglDisplay);
    }
    eglDisplay = EGL14.EGL_NO_DISPLAY;
    eglContext = EGL14.EGL_NO_CONTEXT;
    eglSurface = EGL14.EGL_NO_SURFACE;
  }

  private class Renderer implements SurfaceTexture.OnFrameAvailableListener {
    final ExternalTextureRenderer texRenderer;
    private final float[] matrix;

    Renderer(int textureId, float[] matrix) {
      this.texRenderer = new ExternalTextureRenderer(textureId, sink != null);
      this.matrix = matrix;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
      try {
        surfaceTexture.updateTexImage();
        texRenderer.renderFrame(matrix != null ? matrix : scaler.landscapeMvpMatrix);
        if (sink != null) {
          sink.postFrame();
        } else {
          EGL14.eglSwapBuffers(eglDisplay, eglSurface);
        }
        if (matrix == null && crop) {
          scaler.onFrame();
        }
      } catch (Exception e) {
        Log.e(TAG, "failed to handle frame", e);
      }
    }
  }
}
