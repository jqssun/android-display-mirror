package android.view;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(KeyEvent.class)
public class KeyEventHidden {
    public int getDisplayId() {
        throw new RuntimeException("Stub!");
    }
    public void setDisplayId(int displayId) {
        throw new RuntimeException("Stub!");
    }
}
