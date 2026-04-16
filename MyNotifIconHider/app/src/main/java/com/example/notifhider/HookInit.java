package com.example.notifhider;

import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

public class HookInit implements IXposedHookLoadPackage {

    private static final int TAG_PKG = "notifhider_pkg".hashCode();

    // Кэш заблокированных пакетов — читаем из XSharedPreferences только раз в 3 секунды
    private static volatile Set<String> blockedCache = Collections.emptySet();
    private static volatile long lastRead = 0;

    private static Set<String> getBlocked() {
        long now = System.currentTimeMillis();
        if (now - lastRead > 3000) {
            lastRead = now;
            blockedCache = MySettings.getBlockedNotifications();
        }
        return blockedCache;
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) return;

        XposedBridge.log("NotifIconHider: загружаемся в SystemUI");

        Class<?> iconViewClass;
        try {
            iconViewClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.StatusBarIconView",
                    lpparam.classLoader);
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log("NotifIconHider: StatusBarIconView не найден: " + e);
            return;
        }

        // === Хук 1: set(icon) — BEFORE ===
        // Если пакет заблокирован — сразу скрываем view и не даём иконке установиться
        boolean hookedSet = false;
        for (Method m : iconViewClass.getDeclaredMethods()) {
            if (m.getName().equals("set") && m.getParameterCount() == 1
                    && !m.getParameterTypes()[0].isPrimitive()) {

                m.setAccessible(true);
                XposedBridge.log("NotifIconHider: нашли set(" + m.getParameterTypes()[0].getName() + ")");

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object icon = param.args[0];
                        if (icon == null) return;

                        String pkg = getPkg(icon);
                        View view = (View) param.thisObject;

                        // Всегда сохраняем пакет в тег — нужно для setVisibility-хука
                        if (pkg != null) {
                            view.setTag(TAG_PKG, pkg);
                        }

                        if (pkg != null && getBlocked().contains(pkg)) {
                            XposedBridge.log("NotifIconHider: блокируем set() для " + pkg);
                            // Скрываем сразу — до того как SystemUI покажет иконку
                            view.setVisibility(View.GONE);
                            // Не отменяем set() совсем, чтобы не было краша,
                            // но сразу же скрываем view
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object icon = param.args[0];
                        if (icon == null) return;

                        String pkg = getPkg(icon);
                        if (pkg == null) return;

                        View view = (View) param.thisObject;
                        view.setTag(TAG_PKG, pkg);

                        if (getBlocked().contains(pkg)) {
                            view.setVisibility(View.GONE);
                        }
                    }
                });

                hookedSet = true;
            }
        }

        if (!hookedSet) {
            XposedBridge.log("NotifIconHider: метод set() не найден");
            return;
        }

        // === Хук 2: setVisibility — BEFORE ===
        // Блокируем любую попытку сделать иконку видимой, если пакет заблокирован
        try {
            XposedHelpers.findAndHookMethod(iconViewClass, "setVisibility", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            int vis = (int) param.args[0];
                            if (vis != View.VISIBLE) return; // разрешаем скрывать

                            View view = (View) param.thisObject;
                            String pkg = (String) view.getTag(TAG_PKG);
                            if (pkg == null) return;

                            if (getBlocked().contains(pkg)) {
                                XposedBridge.log("NotifIconHider: блокируем setVisibility(VISIBLE) для " + pkg);
                                param.setResult(null);
                            }
                        }
                    });
            XposedBridge.log("NotifIconHider: хук setVisibility установлен");
        } catch (Throwable e) {
            XposedBridge.log("NotifIconHider: ошибка setVisibility: " + e);
        }

        // === Хук 3: onAttachedToWindow ===
        // Когда view заново прикрепляется к иерархии (MIUI пересоздаёт views) — скрываем
        try {
            XposedHelpers.findAndHookMethod(iconViewClass, "onAttachedToWindow",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            View view = (View) param.thisObject;
                            String pkg = (String) view.getTag(TAG_PKG);
                            if (pkg == null) return;

                            if (getBlocked().contains(pkg)) {
                                view.setVisibility(View.GONE);
                            }
                        }
                    });
            XposedBridge.log("NotifIconHider: хук onAttachedToWindow установлен");
        } catch (Throwable e) {
            XposedBridge.log("NotifIconHider: ошибка onAttachedToWindow: " + e);
        }

        XposedBridge.log("NotifIconHider: все хуки установлены");
    }

    private static String getPkg(Object icon) {
        for (String field : new String[]{"pkg", "mPackageName", "packageName"}) {
            try {
                String val = (String) XposedHelpers.getObjectField(icon, field);
                if (val != null) return val;
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
