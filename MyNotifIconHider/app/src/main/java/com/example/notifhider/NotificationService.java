package com.example.notifhider;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

public class NotificationService extends NotificationListenerService {

    private static NotificationService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        instance = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    public static List<StatusBarNotification> fetchAll() {
        List<StatusBarNotification> list = new ArrayList<>();
        if (instance != null) {
            try {
                StatusBarNotification[] active = instance.getActiveNotifications();
                if (active != null) {
                    for (StatusBarNotification sbn : active) {
                        list.add(sbn);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return list;
    }

    public static boolean isConnected() {
        return instance != null;
    }
}
