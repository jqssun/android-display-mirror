package android.view;

public class DisplayInfo {
    public DisplayAddress address;
    public String uniqueId;
    public int rotation;
    public float renderFrameRate;
    public int defaultModeId;
    public int userPreferredModeId;
    public Display.Mode[] supportedModes;
    public Display.Mode[] appsSupportedModes;
    public float refreshRateOverride;
    public int installOrientation;
}
