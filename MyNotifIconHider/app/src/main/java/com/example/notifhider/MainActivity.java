package com.example.notifhider;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LinearLayout container;
    private Button refreshButton;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MySettings.init(this);

        container = findViewById(R.id.notification_container);
        refreshButton = findViewById(R.id.btn_refresh);
        statusText = findViewById(R.id.tv_status);

        refreshButton.setOnClickListener(v -> updateNotificationList());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationList();
    }

    private boolean isNotificationAccessGranted() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        ComponentName cn = new ComponentName(this, NotificationService.class);
        for (String name : flat.split(":")) {
            if (cn.equals(ComponentName.unflattenFromString(name))) return true;
        }
        return false;
    }

    private void updateNotificationList() {
        container.removeAllViews();

        if (!isNotificationAccessGranted()) {
            statusText.setText("Нет доступа к уведомлениям. Нажмите кнопку ниже и включите разрешение для NotifIconHider.");
            refreshButton.setText("Открыть настройки доступа");
            refreshButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            return;
        }

        statusText.setText("Активные уведомления:");
        refreshButton.setText("Обновить список");
        refreshButton.setOnClickListener(v -> updateNotificationList());

        List<StatusBarNotification> list = NotificationService.fetchAll();

        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет активных уведомлений. Убедитесь, что сервис запущен — попробуйте переключить разрешение в настройках.");
            empty.setPadding(16, 24, 16, 16);
            container.addView(empty);
            return;
        }

        for (StatusBarNotification sbn : list) {
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

        @Override
        public void onDestroy() {
            super.onDestroy();
            instance = null;
        }

        public static List<StatusBarNotification> fetchAll() {
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
