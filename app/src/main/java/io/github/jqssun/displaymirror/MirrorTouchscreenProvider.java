package io.github.jqssun.displaymirror;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public class MirrorTouchscreenProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!"displays".equals(uri.getLastPathSegment())) {
            return null;
        }
        return MirrorTouchscreenBridge.createCursor();
    }

    @Override
    public String getType(Uri uri) {
        if ("displays".equals(uri.getLastPathSegment())) {
            return "vnd.android.cursor.dir/vnd." + MirrorTouchscreenBridge.AUTHORITY + ".displays";
        }
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
