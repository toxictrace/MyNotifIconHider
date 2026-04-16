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

import java.util.List;

import android.service.notification.StatusBarNotification;

public class MainActivity extends AppCompatActivity {

    private View layoutNoAccess;
    private View layoutEmpty;
    private RecyclerView recycler;
    private FloatingActionButton fabRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MySettings.init(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        layoutNoAccess = findViewById(R.id.layout_no_access);
        layoutEmpty = findViewById(R.id.layout_empty);
        recycler = findViewById(R.id.recycler);
        fabRefresh = findViewById(R.id.fab_refresh);

        recycler.setLayoutManager(new LinearLayoutManager(this));

        MaterialButton btnGrant = findViewById(R.id.btn_grant);
        btnGrant.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        fabRefresh.setOnClickListener(v -> refresh());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (!isNotificationAccessGranted()) {
            layoutNoAccess.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            recycler.setVisibility(View.GONE);
            return;
        }

        layoutNoAccess.setVisibility(View.GONE);

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
