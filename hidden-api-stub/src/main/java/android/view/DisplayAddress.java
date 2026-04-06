package android.view;

public abstract class DisplayAddress {
    public static final class Physical extends DisplayAddress {
        public int getPort() {
            throw new RuntimeException("stub!");
        }
    }
}
