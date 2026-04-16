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
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {}

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}

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

    public static boolean isAvailable() {
        return instance != null;
    }
}
