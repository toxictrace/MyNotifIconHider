package com.example.notifhider;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.notification.StatusBarNotification;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class MySettings {

    static final String PREF_NAME = "notif_hider_prefs";
    static final String KEY_BLOCKED = "blocked_packages";

    private static Context appContext;

    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
    }

    private static SharedPreferences getPrefs() {
        return appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // После каждого сохранения делаем файл world-readable,
    // чтобы XSharedPreferences мог его прочитать из SystemUI
    private static void makePrefsReadable() {
        try {
            File dir = new File(appContext.getFilesDir().getParent(), "shared_prefs");
            File file = new File(dir, PREF_NAME + ".xml");
            if (file.exists()) {
                file.setReadable(true, false);  // readable by all users
                dir.setExecutable(true, false); // allow traversal
            }
        } catch (Throwable ignored) {}
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
        makePrefsReadable();
    }

    // Вызывается из хука в процессе SystemUI
    public static Set<String> getBlockedNotifications() {
        try {
            XSharedPreferences prefs = new XSharedPreferences("com.example.notifhider", PREF_NAME);
            prefs.makeWorldReadable();
            prefs.reload();
            if (!prefs.getFile().canRead()) {
                XposedBridge.log("NotifIconHider: файл настроек недоступен: " + prefs.getFile().getAbsolutePath());
                return new HashSet<>();
            }
            Set<String> result = prefs.getStringSet(KEY_BLOCKED, new HashSet<>());
            XposedBridge.log("NotifIconHider: заблокированных пакетов: " + result.size() + " -> " + result);
            return result;
        } catch (Throwable t) {
            XposedBridge.log("NotifIconHider: ошибка XSharedPreferences: " + t);
            return new HashSet<>();
        }
    }
}
