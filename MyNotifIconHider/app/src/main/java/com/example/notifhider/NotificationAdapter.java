package com.example.notifhider;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.service.notification.StatusBarNotification;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private final List<StatusBarNotification> items;
    private final PackageManager pm;

    public NotificationAdapter(List<StatusBarNotification> items, PackageManager pm) {
        this.items = items;
        this.pm = pm;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StatusBarNotification sbn = items.get(position);
        String pkg = sbn.getPackageName();

        // Иконка приложения
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            Drawable icon = pm.getApplicationIcon(info);
            holder.ivIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        // Название приложения
        String appName;
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            appName = pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            appName = pkg;
        }
        holder.tvAppName.setText(appName);

        // Заголовок/текст уведомления
        Notification n = sbn.getNotification();
        String title = n.extras.getString(Notification.EXTRA_TITLE, "");
        String text = n.extras.getString(Notification.EXTRA_TEXT, "");
        String sub = title.isEmpty() ? text : (text.isEmpty() ? title : title + " — " + text);
        holder.tvTitle.setText(sub);

        // Переключатель — убираем listener перед setChecked чтобы не было петли
        holder.switchHide.setOnCheckedChangeListener(null);
        holder.switchHide.setChecked(MySettings.isBlocked(pkg));
        holder.switchHide.setOnCheckedChangeListener((btn, isChecked) ->
                MySettings.setBlocked(pkg, isChecked));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvAppName;
        TextView tvTitle;
        SwitchMaterial switchHide;

        ViewHolder(View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_icon);
            tvAppName = v.findViewById(R.id.tv_app_name);
            tvTitle = v.findViewById(R.id.tv_title);
            switchHide = v.findViewById(R.id.switch_hide);
        }
    }
}
