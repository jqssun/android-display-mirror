package io.github.jqssun.displaymirror.job;

import android.opengl.GLES20;
import io.github.jqssun.displaymirror.DisplaylinkState;
import io.github.jqssun.displaymirror.State;
import java.nio.ByteBuffer;

// reads rendered frames from the bound framebuffer and posts them to the displaylink driver
public class DisplaylinkSender implements StreamRenderer.FrameSink {
  private final DisplaylinkState displaylinkState;
  private final int width;
  private final int height;
  private final ByteBuffer[] buffers;
  private int buffersIndex;

  public DisplaylinkSender(int width, int height) {
    this.width = width;
    this.height = height;
    buffers =
        new ByteBuffer[] {
          ByteBuffer.allocateDirect(width * height * 4),
          ByteBuffer.allocateDirect(width * height * 4),
          ByteBuffer.allocateDirect(width * height * 4),
        };
    displaylinkState = State.displaylinkState;
  }

  @Override
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
