package com.example.notifhider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.HashSet;
import java.util.Set;

public class PrefsProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Context ctx = getContext();
        if (ctx == null) return null;

        Set<String> blocked = ctx
                .getSharedPreferences(MySettings.PREF_NAME, Context.MODE_PRIVATE)
                .getStringSet(MySettings.KEY_BLOCKED, new HashSet<>());

        MatrixCursor cursor = new MatrixCursor(new String[]{"pkg"});
        for (String pkg : blocked) {
            cursor.addRow(new Object[]{pkg});
        }
        return cursor;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
