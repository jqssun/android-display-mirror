package io.github.jqssun.displaymirror.job;

import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import io.github.jqssun.displaymirror.DisplaylinkState;
import io.github.jqssun.displaymirror.State;
import com.displaylink.manager.display.DisplayMode;

import java.nio.ByteBuffer;

public class ListenImageReaderAndPostFrame implements ImageReader.OnImageAvailableListener {

    private DisplaylinkState displaylinkState;
    private boolean hasSetMode = false;
    private Image lastImage;
    private int monitorWidth;
    private int monitorHeight;
    private int refreshRate;
    private int frameCounter = 0;
    private long lastFrameTimeStamp = System.currentTimeMillis();

    public ListenImageReaderAndPostFrame(VirtualDisplayArgs virtualDisplayArgs) {
        this.displaylinkState = State.displaylinkState;
        this.monitorWidth = virtualDisplayArgs.width;
        this.monitorHeight = virtualDisplayArgs.height;
        this.refreshRate = virtualDisplayArgs.refreshRate;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        frameCounter++;
        if (frameCounter % 240 == 0) {
            long currentTime = System.currentTimeMillis();
            long timeElapsed = currentTime - lastFrameTimeStamp;
            float frameDuration = ((float)timeElapsed) / ((float)240);
            if (State.displaylinkState.frameDuration <= 0) {
                State.displaylinkState.frameDuration = frameDuration;
            } else if (frameDuration < State.displaylinkState.frameDuration) {
                State.displaylinkState.frameDuration = frameDuration;
            }
            lastFrameTimeStamp = currentTime;
        }
        try {
            Image thisImage = displaylinkState.imageReader.acquireNextImage();
            Image.Plane plane = thisImage.getPlanes()[0];
            if (!hasSetMode) {
                hasSetMode = true;
                displaylinkState.nativeDriver.setMode(displaylinkState.encoderId, new DisplayMode(monitorWidth, monitorHeight, refreshRate), plane.getRowStride(), 1);
            }
            ByteBuffer buffer = plane.getBuffer();
            int resultCode = displaylinkState.nativeDriver.postFrame(displaylinkState.encoderId, buffer);
            if (resultCode < 0) {
                Log.e("displaylink", "postFrame failed, resultCode: " + resultCode);
            }
            boolean buffered = resultCode != 1 && resultCode != -2;
            if (buffered) {
                if (lastImage != null) {
                    lastImage.close();
                }
                lastImage = thisImage;
            } else{
                thisImage.close();
            }
        } catch (Throwable e) {
            Log.e("ImageReader", "failed to post frame", e);
        }
    }
}