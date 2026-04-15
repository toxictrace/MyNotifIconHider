package com.example.notifhider;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LinearLayout container;
    private Button refreshButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MySettings.init(this);

        container = findViewById(R.id.notification_container);
        refreshButton = findViewById(R.id.btn_refresh);

        refreshButton.setOnClickListener(v -> updateNotificationList());
        updateNotificationList();
    }

    private void updateNotificationList() {
        container.removeAllViews();
        List<StatusBarNotification> activeNotifications = NotificationService.getActiveNotifications();

        for (StatusBarNotification sbn : activeNotifications) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(16, 16, 16, 16);

            TextView text = new TextView(this);
            Notification n = sbn.getNotification();
            String title = n.extras.getString(Notification.EXTRA_TITLE, "");
            String textContent = n.extras.getString(Notification.EXTRA_TEXT, "");
            text.setText(sbn.getPackageName() + "\n" + title + " : " + textContent);
            text.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Switch toggle = new Switch(this);
            toggle.setChecked(MySettings.isBlocked(sbn));
            toggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                MySettings.setBlocked(sbn, isChecked));

            row.addView(text);
            row.addView(toggle);
            container.addView(row);
        }
    }

    public static class NotificationService extends NotificationListenerService {
        private static NotificationService instance;

        @Override
        public void onCreate() {
            super.onCreate();
            instance = this;
        }

        public static List<StatusBarNotification> getActiveNotifications() {
            List<StatusBarNotification> list = new ArrayList<>();
            if (instance != null) {
                StatusBarNotification[] active = instance.getActiveNotifications();
                if (active != null) {
                    for (StatusBarNotification sbn : active) {
                        list.add(sbn);
                    }
                }
            }
            return list;
        }
    }
}
