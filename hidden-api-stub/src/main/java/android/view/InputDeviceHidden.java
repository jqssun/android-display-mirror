package android.view;

import android.hardware.input.InputDeviceIdentifier;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(InputDevice.class)
public class InputDeviceHidden {
    public InputDeviceIdentifier getIdentifier() {
        throw new RuntimeException("stub!");
    }
}
