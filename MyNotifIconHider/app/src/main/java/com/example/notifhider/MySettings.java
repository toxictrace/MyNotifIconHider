package com.example.notifhider;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.notification.StatusBarNotification;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class MySettings {

    private static final String PREF_NAME = "notif_hider_prefs";
    private static final String KEY_BLOCKED = "blocked_packages";

    private static Context appContext;

    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
    }

    @SuppressWarnings("deprecation")
    private static SharedPreferences getPrefs() {
        // MODE_WORLD_READABLE нужен, чтобы XSharedPreferences мог читать из процесса SystemUI.
        // На Android 7+ может бросить SecurityException — перехватываем и откатываемся к PRIVATE.
        try {
            return appContext.getSharedPreferences(PREF_NAME, Context.MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            return appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    public static boolean isBlocked(StatusBarNotification sbn) {
        Set<String> blocked = getPrefs().getStringSet(KEY_BLOCKED, new HashSet<>());
        return blocked.contains(sbn.getPackageName());
    }

    public static void setBlocked(StatusBarNotification sbn, boolean blocked) {
        SharedPreferences prefs = getPrefs();
        Set<String> current = new HashSet<>(prefs.getStringSet(KEY_BLOCKED, new HashSet<>()));
        if (blocked) {
            current.add(sbn.getPackageName());
        } else {
            current.remove(sbn.getPackageName());
        }
        prefs.edit().putStringSet(KEY_BLOCKED, current).apply();
    }

    // Вызывается из хука — читает настройки из файла напрямую (другой процесс)
    public static Set<String> getBlockedNotifications() {
        try {
            XSharedPreferences prefs = new XSharedPreferences("com.example.notifhider", PREF_NAME);
            prefs.makeWorldReadable();
            prefs.reload();
            Set<String> result = prefs.getStringSet(KEY_BLOCKED, new HashSet<>());
            XposedBridge.log("NotifIconHider: заблокированные пакеты: " + result);
            return result;
        } catch (Throwable t) {
            XposedBridge.log("NotifIconHider: ошибка чтения настроек: " + t);
            return new HashSet<>();
        }
    }
}
