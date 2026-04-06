package android.view;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(MotionEvent.class)
public class MotionEventHidden {
    public final void setActionButton(int button) {
        throw new RuntimeException("Stub!");
    }
    public int getActionButton() {
        throw new RuntimeException("Stub!");
    }
    public int getDisplayId() {
        throw new RuntimeException("Stub!");
    }
    public void setDisplayId(int displayId) {
        throw new RuntimeException("Stub!");
    }
}
