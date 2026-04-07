package io.github.jqssun.displaymirror.job;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.content.SharedPreferences;

import io.github.jqssun.displaymirror.DisplaylinkState;
import io.github.jqssun.displaymirror.FloatingButtonService;
import io.github.jqssun.displaymirror.Pref;
import io.github.jqssun.displaymirror.State;

import java.nio.ByteBuffer;

public class AutoRotateAndScaleForDisplaylink {
    public static AutoRotateAndScaleForDisplaylink instance;
    private final VirtualDisplayArgs virtualDisplayArgs;
    private OrientationChangeCallback orientationChangeCallback;
    private EGLDisplay eglDisplay;
    private EGLConfig eglConfig;
    private EGLContext eglContext;
    private int[] fbo = new int[1];
    private int[] tempTexture = new int[1];
    private int portraitInputTextureId;
    private int landscapeInputTextureId;
    private PortraitRenderer portraitRenderer;
    private LandscapeRenderer landscapeRenderer;
    private boolean autoRotate;
    private boolean autoScale;
    private SurfaceTexture portraitInputSurfaceTexture;
    private Surface portraitInputSurface;
    private SurfaceTexture landscapeInputSurfaceTexture;
    private Surface landscapeInputSurface;
    private Surface currentSurface;
    private int virtualDisplayId;

    public AutoRotateAndScaleForDisplaylink(VirtualDisplayArgs virtualDisplayArgs, Context context) {
        if (instance != null) {
            instance.release();
        }
        instance = this;
        this.virtualDisplayArgs = virtualDisplayArgs;
        
        // read settings from SharedPreferences
        autoRotate = Pref.getAutoRotate();
        autoScale = Pref.getAutoScale();
        
        DisplaylinkState displaylinkState = State.displaylinkState;
        displaylinkState.handlerThread = new HandlerThread("ListenOpenglAndPostFrame");
        displaylinkState.handlerThread.start();
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        displaylinkState.handler = new Handler(displaylinkState.handlerThread.getLooper());
        displaylinkState.handler.post(() -> start(displayManager));
        // only register orientation change listener when autoRotate is true
        if (autoRotate) {
            orientationChangeCallback = new OrientationChangeCallback();
            displayManager.registerDisplayListener(orientationChangeCallback, null);
        }
    }

    public void release() {
        instance = null;
        Context context = State.getContext();
        if (orientationChangeCallback != null && context != null) {
            DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            displayManager.unregisterDisplayListener(orientationChangeCallback);
        }
        
        // release resources on the OpenGL thread
        if (State.displaylinkState != null && State.displaylinkState.handler != null) {
            State.displaylinkState.handler.post(() -> {
                // release Surface and SurfaceTexture
                if (portraitInputSurface != null) {
                    portraitInputSurface.release();
                    portraitInputSurface = null;
                }
                if (portraitInputSurfaceTexture != null) {
                    portraitInputSurfaceTexture.release();
                    portraitInputSurfaceTexture = null;
                }
                if (landscapeInputSurface != null) {
                    landscapeInputSurface.release();
                    landscapeInputSurface = null;
                }
                if (landscapeInputSurfaceTexture != null) {
                    landscapeInputSurfaceTexture.release();
                    landscapeInputSurfaceTexture = null;
                }
                
                // release renderers
                if (portraitRenderer != null) {
                    portraitRenderer.release();
                    portraitRenderer = null;
                }
                if (landscapeRenderer != null) {
                    landscapeRenderer.release();
                    landscapeRenderer = null;
                }
                
                // delete OpenGL textures and FBO
                if (tempTexture[0] != 0) {
                    GLES20.glDeleteTextures(1, tempTexture, 0);
                    tempTexture[0] = 0;
                }
                if (fbo[0] != 0) {
                    GLES20.glDeleteFramebuffers(1, fbo, 0);
                    fbo[0] = 0;
                }
                
                // release EGL resources
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    if (eglContext != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(eglDisplay, eglContext);
                        eglContext = EGL14.EGL_NO_CONTEXT;
                    }
                    EGL14.eglTerminate(eglDisplay);
                    eglDisplay = EGL14.EGL_NO_DISPLAY;
                }
            });
        }
    }

    private class OrientationChangeCallback implements DisplayManager.DisplayListener {

        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {
            if (virtualDisplayId == displayId) {
                android.util.Log.i("ListenOpenglAndPostFrame", "display removed, release");
                release();
            }
        }

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                updateSurface();
                new Handler().postDelayed(() -> {
                    updateSurface();
                }, 1000);
            }
        }
    }

    private void updateSurface() {
        Context context = State.getContext();
        if (context == null) {
            return;
        }
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        defaultDisplay.getRealMetrics(metrics);
        boolean isLandscape = metrics.widthPixels > metrics.heightPixels;
        Surface newSurface = isLandscape ? landscapeInputSurface : portraitInputSurface;
        if (newSurface != currentSurface && State.displaylinkState.getVirtualDisplay() != null) {
            currentSurface = newSurface;
            if (isLandscape) {
                State.displaylinkState.getVirtualDisplay().resize(virtualDisplayArgs.width, virtualDisplayArgs.height, 160);
            } else {
                State.displaylinkState.getVirtualDisplay().resize(virtualDisplayArgs.height, virtualDisplayArgs.width, 160);
            }
            State.displaylinkState.getVirtualDisplay().setSurface(currentSurface);
        }
    }

    public void start(DisplayManager displayManager) {
        int width = virtualDisplayArgs.width;
        int height = virtualDisplayArgs.height;
        // initialize EGL
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("Failed to get EGL display");
        }

        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw new RuntimeException("Failed to initialize EGL");
        }

        // configure EGL
        int[] configAttribs = {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
        };

        android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
        int[] numConfigs = new int[1];
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0);
        eglConfig = configs[0];

        // create EGL context
        int[] contextAttribs = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0);

        // create EGL Surface
        int[] surfaceAttribs = {
            EGL14.EGL_NONE
        };
        EGLSurface eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttribs, 0);
        if (eglSurface == null) {
            throw new RuntimeException("Failed to create EGL offscreen buffer surface");
        }

        // set current context
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("Failed to set EGL context as current");
        }
        GLES20.glViewport(0, 0, width, height);

        // create temporary texture
        GLES20.glGenTextures(1, tempTexture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tempTexture[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,  // set height to full height
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // create and configure FBO
        GLES20.glGenFramebuffers(1, fbo, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, tempTexture[0], 0);

        // check FBO status
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            android.util.Log.e("MirrorActivity", "FBO creation failed, status: " + status);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);

        int[] textures = new int[2];
        GLES20.glGenTextures(2, textures, 0);
        portraitInputTextureId = textures[0];
        landscapeInputTextureId = textures[1];

        portraitRenderer = new PortraitRenderer(portraitInputTextureId, width, height);
        landscapeRenderer = new LandscapeRenderer(landscapeInputTextureId, width, height, autoScale);

        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, portraitInputTextureId);

        // set texture parameters
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, landscapeInputTextureId);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);


        // create SurfaceTexture and Surface
        portraitInputSurfaceTexture = new SurfaceTexture(portraitInputTextureId);
        portraitInputSurfaceTexture.setDefaultBufferSize(height, width);
        portraitInputSurfaceTexture.setOnFrameAvailableListener(portraitRenderer);
        portraitInputSurface = new Surface(portraitInputSurfaceTexture);

        landscapeInputSurfaceTexture = new SurfaceTexture(landscapeInputTextureId);
        landscapeInputSurfaceTexture.setDefaultBufferSize(width, height);
        landscapeInputSurfaceTexture.setOnFrameAvailableListener(landscapeRenderer);
        landscapeInputSurface = new Surface(landscapeInputSurfaceTexture);

        DisplaylinkState displaylinkState = State.displaylinkState;
        if (displaylinkState.getVirtualDisplay() == null && State.getMediaProjection() != null) {
            displaylinkState.stopVirtualDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
            defaultDisplay.getRealMetrics(metrics);
            boolean isLandscape = metrics.widthPixels > metrics.heightPixels;
            if (!autoRotate) {
                isLandscape = true;
            }
            currentSurface = isLandscape ? landscapeInputSurface : portraitInputSurface;
            displaylinkState.createdVirtualDisplay(State.getMediaProjection().createVirtualDisplay("Displaylink Mirror",
                    isLandscape ? width : height, isLandscape ? height : width, 160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    currentSurface, null, displaylinkState.handler));
            State.setMediaProjection(null);
            FloatingButtonService.startForMirror();
            CreateVirtualDisplay.powerOffScreen();
        } else if (displaylinkState.getVirtualDisplay() != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
            defaultDisplay.getRealMetrics(metrics);
            boolean isLandscape = metrics.widthPixels > metrics.heightPixels;
            currentSurface = isLandscape ? landscapeInputSurface : portraitInputSurface;
            displaylinkState.getVirtualDisplay().setSurface(currentSurface);
        }
        if (displaylinkState.getVirtualDisplay() == null) {
            this.release();
        } else {
            virtualDisplayId = displaylinkState.getVirtualDisplay().getDisplay().getDisplayId();
        }
    }

    private static class DisplaylinkSender {
        private final DisplaylinkState displaylinkState;
        private final int width;
        private final int height;
        private ByteBuffer[] buffers;
        private int buffersIndex;

        public DisplaylinkSender(int width, int height) {
            this.width = width;
            this.height = height;
            buffers = new ByteBuffer[] {
                    ByteBuffer.allocateDirect(width * height * 4),
                    ByteBuffer.allocateDirect(width * height * 4),
                    ByteBuffer.allocateDirect(width * height * 4),
            };
            displaylinkState = State.displaylinkState;
        }

        public void postFrame() {
            GLES20.glFinish();
            ByteBuffer buffer = buffers[buffersIndex];
            buffer.position(0);
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
            buffer.rewind();
            int resultCode = displaylinkState.nativeDriver.postFrame(displaylinkState.encoderId, buffer);
            boolean buffered = resultCode != 1 && resultCode != -2;
            if (buffered) {
                buffersIndex = (buffersIndex + 1) % buffers.length;
            }
        }
    }

    private static class PortraitRenderer implements SurfaceTexture.OnFrameAvailableListener {
        private final ExternalTextureRenderer externalTextureRenderer;
        private final DisplaylinkSender displaylinkSender;
        protected float[] portraitMvpMatrix;

        public PortraitRenderer(int inputTextureId, int width, int height) {
            this.externalTextureRenderer = new ExternalTextureRenderer(inputTextureId, true);
            this.displaylinkSender = new DisplaylinkSender(width, height);
            portraitMvpMatrix = new float[16];
            android.opengl.Matrix.setIdentityM(portraitMvpMatrix, 0);
            android.opengl.Matrix.scaleM(portraitMvpMatrix, 0, 1, 1, 1.0f);
            android.opengl.Matrix.setRotateM(portraitMvpMatrix, 0, 270, 0, 0, 1.0f);
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            try {
                surfaceTexture.updateTexImage();
                this.externalTextureRenderer.renderFrame(portraitMvpMatrix);
                displaylinkSender.postFrame();
            } catch(Exception e) {
                android.util.Log.e("ListenOpenglAndPostFrame", "failed to handle frame", e);
            }
        }

        public void release() {
            this.externalTextureRenderer.release();
        }
    }

    private static class LandscapeRenderer implements SurfaceTexture.OnFrameAvailableListener {
        private final ExternalTextureRenderer externalTextureRenderer;
        private final boolean autoScale;
        private final LandscapeAutoScaler landscapeAutoScaler;
        private final DisplaylinkSender displaylinkSender;

        public LandscapeRenderer(int inputTextureId, int width, int height, boolean autoScale) {
            this.externalTextureRenderer = new ExternalTextureRenderer(inputTextureId, true);
            this.autoScale = autoScale;
            this.landscapeAutoScaler = new LandscapeAutoScaler(externalTextureRenderer, width, height, 0);
            this.displaylinkSender = new DisplaylinkSender(width, height);
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            surfaceTexture.updateTexImage();
            externalTextureRenderer.renderFrame(landscapeAutoScaler.landscapeMvpMatrix);
            displaylinkSender.postFrame();
            if (autoScale) {
                landscapeAutoScaler.onFrame();
            }
        }

        public void release() {
            this.externalTextureRenderer.release();
        }
    }
}
