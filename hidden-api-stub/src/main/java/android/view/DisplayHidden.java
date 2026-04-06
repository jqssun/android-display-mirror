package android.view;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(Display.class)
public class DisplayHidden {
    public int getType() {
        throw new RuntimeException("stub!");
    }
}
