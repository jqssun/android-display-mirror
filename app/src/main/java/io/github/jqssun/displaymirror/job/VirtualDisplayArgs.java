package io.github.jqssun.displaymirror.job;

import android.os.Parcel;
import android.os.Parcelable;

public class VirtualDisplayArgs implements Parcelable {
    public final int width;
    public final int height;
    public final int refreshRate;
    public int dpi;
    public final boolean rotatesWithContent;
    public final String virtualDisplayName;

    public VirtualDisplayArgs() {
        this("DisplayLink", 1920, 1080, 60, 160, false);
    }
    public VirtualDisplayArgs(String virtualDisplayName, int width, int height, int refreshRate, int dpi, boolean rotatesWithContent) {
        this.virtualDisplayName = virtualDisplayName;
        if(width == 0) {
            width = 1920;
        }
        if (height == 0) {
            height = 1080;
        }
        if (refreshRate == 0) {
            refreshRate = 60;
        }
        if (dpi == 0) {
            dpi = 160;
        }
        this.width = width;
        this.height = height;
        this.refreshRate = refreshRate;
        this.dpi = dpi;
        this.rotatesWithContent = rotatesWithContent;
    }

    protected VirtualDisplayArgs(Parcel in) {
        virtualDisplayName = in.readString();
        width = in.readInt();
        height = in.readInt();
        refreshRate = in.readInt();
        dpi = in.readInt();
        rotatesWithContent = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(virtualDisplayName);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeInt(refreshRate);
        dest.writeInt(dpi);
        dest.writeByte((byte) (rotatesWithContent ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<VirtualDisplayArgs> CREATOR = new Creator<VirtualDisplayArgs>() {
        @Override
        public VirtualDisplayArgs createFromParcel(Parcel in) {
            return new VirtualDisplayArgs(in);
        }

        @Override
        public VirtualDisplayArgs[] newArray(int size) {
            return new VirtualDisplayArgs[size];
        }
    };
}