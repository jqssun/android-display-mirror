package android.media.projection;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(MediaProjection.class)
public class MediaProjectionHidden {
    public IMediaProjection getProjection() {
        throw new RuntimeException("stub!");
    }
}
