package com.displaylink.manager.display;

import java.util.Arrays;

public class MonitorInfo {
    public final DisplayMode[] a; /* DisplayMode[] */
    public final String b; /* MonitorName */
    public final String c;
    public final String d;
    public final int e;
    public final int f;
    public final boolean g;
    public final boolean h;
    public final boolean i;

    public MonitorInfo(DisplayMode[] displayModeArr, String str, String str2, String str3, int i, int i2, boolean z, boolean z2, boolean z3) {
        this.a = displayModeArr;
        this.b = str;
        this.c = str2;
        this.d = str3;
        this.e = i;
        this.f = i2;
        this.g = z;
        this.h = z2;
        this.i = z3;
    }

    public String toString() {
        return Arrays.toString(a);
    }
}
