package com.displaylink.manager.display;

public class DisplayMode {
    public final int height;
    public final int refreshRate;
    public final int width;

    public DisplayMode(int i, int i2, int i3) {
        this.width = i;
        this.height = i2;
        this.refreshRate = i3;
    }

    public String toString() {
        return "DisplayMode{" +
                "width=" + width +
                ", height=" + height +
                ", refreshRate=" + refreshRate +
                '}';
    }
}
