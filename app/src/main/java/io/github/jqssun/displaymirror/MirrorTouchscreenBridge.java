package io.github.jqssun.displaymirror;

import android.content.Context;
import android.content.Intent;
import android.database.MatrixCursor;
import android.net.Uri;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

public final class MirrorTouchscreenBridge {
    public static final String ACTION_OPEN_TOUCHSCREEN = "io.github.jqssun.displaymirror.action.OPEN_TOUCHSCREEN";
    public static final String EXTRA_DISPLAY_ID = "display_id";
    public static final String AUTHORITY = "io.github.jqssun.displaymirror.touchscreen";
    public static final Uri DISPLAYS_URI = Uri.parse("content://" + AUTHORITY + "/displays");
    public static final String COLUMN_DISPLAY_ID = "display_id";
    public static final String COLUMN_TYPE = "type";
    public static final String TYPE_MOONLIGHT = "moonlight";
    public static final String TYPE_DISPLAYLINK = "displaylink";
    public static final String TYPE_AIRPLAY = "airplay";

    private MirrorTouchscreenBridge() {
    }

    public static final class TargetInfo {
        public final int displayId;
        public final String type;
        public final Surface surface;

        private TargetInfo(int displayId, String type, Surface surface) {
            this.displayId = displayId;
            this.type = type;
            this.surface = surface;
        }
    }

    public static List<TargetInfo> getActiveTargets() {
        List<TargetInfo> targets = new ArrayList<>();
        _addTarget(targets, State.getMirrorVirtualDisplayId(), TYPE_MOONLIGHT,
                State.mirrorVirtualDisplay != null ? State.mirrorVirtualDisplay.getSurface() : null);
        _addTarget(targets, State.getDisplaylinkVirtualDisplayId(), TYPE_DISPLAYLINK,
                State.displaylinkState.getVirtualDisplay() != null ? State.displaylinkState.getVirtualDisplay().getSurface() : null);
        _addTarget(targets, State.getAirPlayVirtualDisplayId(), TYPE_AIRPLAY, State.getAirPlaySurface());
        return targets;
    }

    public static TargetInfo getDefaultTarget() {
        List<TargetInfo> targets = getActiveTargets();
        return targets.isEmpty() ? null : targets.get(0);
    }

    public static TargetInfo findTarget(int displayId) {
        for (TargetInfo target : getActiveTargets()) {
            if (target.displayId == displayId) {
                return target;
            }
        }
        return null;
    }

    public static Intent createInternalIntent(Context context, TargetInfo target) {
        Intent intent = new Intent(context, TouchscreenActivity.class);
        intent.putExtra("display", target.displayId);
        intent.putExtra("surface", target.surface);
        return intent;
    }

    public static MatrixCursor createCursor() {
        MatrixCursor cursor = new MatrixCursor(new String[]{COLUMN_DISPLAY_ID, COLUMN_TYPE});
        for (TargetInfo target : getActiveTargets()) {
            cursor.addRow(new Object[]{target.displayId, target.type});
        }
        return cursor;
    }

    private static void _addTarget(List<TargetInfo> targets, int displayId, String type, Surface surface) {
        if (displayId <= 0 || surface == null || !surface.isValid()) {
            return;
        }
        for (TargetInfo existing : targets) {
            if (existing.displayId == displayId) {
                return;
            }
        }
        targets.add(new TargetInfo(displayId, type, surface));
    }
}
