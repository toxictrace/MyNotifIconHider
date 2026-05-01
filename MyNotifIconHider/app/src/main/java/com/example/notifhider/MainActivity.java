package com.example.notifhider;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class MainActivity extends AppCompatActivity {

    private View layoutNoAccess;
    private View layoutEmpty;
    private RecyclerView recycler;
    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MySettings.init(this);

        rootView = findViewById(android.R.id.content);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        layoutNoAccess = findViewById(R.id.layout_no_access);
        layoutEmpty = findViewById(R.id.layout_empty);
        recycler = findViewById(R.id.recycler);

        recycler.setLayoutManager(new LinearLayoutManager(this));

        MaterialButton btnGrant = findViewById(R.id.btn_grant);
        btnGrant.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        FloatingActionButton fabRefresh = findViewById(R.id.fab_refresh);
        fabRefresh.setOnClickListener(v -> {
            tryRebind();
            refresh();
        });

        // Обработчик меню тулбара
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_restart_systemui) {
                restartSystemUI();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Пробуем переподключить сервис при каждом открытии
        tryRebind();
        refresh();
    }

    private void tryRebind() {
        if (isNotificationAccessGranted() && !NotificationService.isConnected()) {
            // Сервис имеет разрешение, но не подключён — просим Android перезапустить
            try {
                ComponentName cn = new ComponentName(this, NotificationService.class);
                NotificationListenerService.requestRebind(cn);
            } catch (Throwable ignored) {}
        }
    }

    private void refresh() {
        if (!isNotificationAccessGranted()) {
            layoutNoAccess.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            recycler.setVisibility(View.GONE);
            return;
        }

        layoutNoAccess.setVisibility(View.GONE);

        // Разрешение есть, но сервис ещё не подключился (бывает при смене компонента)
        if (!NotificationService.isConnected()) {
            showToggleHint();
            layoutEmpty.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
            return;
        }

        List<StatusBarNotification> list = NotificationService.fetchAll();

        if (list.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            recycler.setAdapter(new NotificationAdapter(list, getPackageManager()));
        }
    }

    /**
     * Перезагружает SystemUI через root.
     * Android автоматически перезапускает SystemUI как постоянный сервис.
     */
    private void restartSystemUI() {
        Snackbar.make(rootView, R.string.restarting_systemui, Snackbar.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Process su = Runtime.getRuntime().exec("su");
                su.getOutputStream().write("pkill -f com.android.systemui\n".getBytes());
                su.getOutputStream().flush();
                su.getOutputStream().write("exit\n".getBytes());
                su.getOutputStream().flush();
                su.waitFor();
            } catch (Throwable t) {
                runOnUiThread(() ->
                        Snackbar.make(rootView, R.string.restart_no_root,
                                Snackbar.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void showToggleHint() {
        Snackbar.make(rootView,
                "Сервис не подключён — выключи и снова включи разрешение в настройках",
                Snackbar.LENGTH_LONG)
                .setAction("Открыть", v ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .show();
    }

    private boolean isNotificationAccessGranted() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        ComponentName cn = new ComponentName(this, NotificationService.class);
        for (String name : flat.split(":")) {
            ComponentName c = ComponentName.unflattenFromString(name);
            if (cn.equals(c)) return true;
        }
        return false;
    }
}
