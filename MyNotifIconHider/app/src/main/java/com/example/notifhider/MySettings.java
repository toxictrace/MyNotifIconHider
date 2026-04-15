package com.example.notifhider;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.service.notification.StatusBarNotification;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XSharedPreferences;

public class MySettings {

    private static final String PREF_NAME = "notif_hider_prefs";
    private static final String KEY_BLOCKED = "blocked_packages";

    private static Context appContext;

    // Инициализировать из MainActivity
    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
    }

    // Используется в MainActivity
    public static boolean isBlocked(StatusBarNotification sbn) {
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> blocked = prefs.getStringSet(KEY_BLOCKED, new HashSet<>());
        return blocked.contains(sbn.getPackageName());
    }

    // Используется в MainActivity
    public static void setBlocked(StatusBarNotification sbn, boolean blocked) {
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> current = new HashSet<>(prefs.getStringSet(KEY_BLOCKED, new HashSet<>()));
        if (blocked) {
            current.add(sbn.getPackageName());
        } else {
            current.remove(sbn.getPackageName());
        }
        prefs.edit().putStringSet(KEY_BLOCKED, current).apply();
    }

    // Используется в HookInit (читает файл настроек напрямую из другого процесса)
    public static Set<String> getBlockedNotifications() {
        XSharedPreferences prefs = new XSharedPreferences("com.example.notifhider", PREF_NAME);
        prefs.reload();
        return prefs.getStringSet(KEY_BLOCKED, new HashSet<>());
    }
}
