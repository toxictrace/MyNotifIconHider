package com.example.notifhider;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class HookInit implements IXposedHookLoadPackage {

    private static final int TAG_PKG = "notifhider_pkg".hashCode();
    private static final Uri PREFS_URI =
            Uri.parse("content://com.example.notifhider.prefs/blocked");

    private static volatile Set<String> blockedCache = Collections.emptySet();
    private static volatile long lastRead = 0;
    private static volatile Context cachedCtx = null;

    private static Set<String> getBlocked() {
        long now = System.currentTimeMillis();
        if (cachedCtx != null && now - lastRead > 1000) {
            Set<String> fresh = readFromProvider(cachedCtx);
            if (fresh != null) {
                // null = провайдер недоступен → не обновляем lastRead, пробуем снова
                blockedCache = fresh;
                lastRead = now;
            }
        }
        return blockedCache;
    }

    /**
     * Возвращает набор заблокированных пакетов.
     * Возвращает null если ContentProvider недоступен (приложение ещё не запущено).
     * Возвращает пустой Set если провайдер доступен, но список пуст.
     */
    private static Set<String> readFromProvider(Context ctx) {
        Set<String> result = new HashSet<>();
        try {
            Cursor cursor = ctx.getContentResolver().query(
                    PREFS_URI, null, null, null, null);
            if (cursor == null) {
                // Провайдер ещё не поднялся — не кэшируем
                XposedBridge.log("NotifIconHider: ContentProvider недоступен, повтор...");
                return null;
            }
            try {
                while (cursor.moveToNext()) {
                    result.add(cursor.getString(0));
                }
            } finally {
                cursor.close();
            }
            XposedBridge.log("NotifIconHider: заблокировано: " + result);
        } catch (Throwable t) {
            XposedBridge.log("NotifIconHider: ошибка ContentProvider: " + t);
            return null; // не кэшируем при ошибке
        }
        return result;
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

        final Class<?> finalIconViewClass = iconViewClass;

        // === Хук 1: set(StatusBarIcon) ===
        boolean hookedSet = false;
        for (Method m : iconViewClass.getDeclaredMethods()) {
            if (m.getName().equals("set") && m.getParameterCount() == 1
                    && !m.getParameterTypes()[0].isPrimitive()) {

                m.setAccessible(true);
                XposedBridge.log("NotifIconHider: нашли set("
                        + m.getParameterTypes()[0].getName() + ")");

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        if (cachedCtx == null) {
                            cachedCtx = view.getContext().getApplicationContext();
                        }
                        Object icon = param.args[0];
                        if (icon == null) return;
                        String pkg = getPkg(icon);
                        if (pkg != null) view.setTag(TAG_PKG, pkg);
                        if (pkg != null && getBlocked().contains(pkg)) {
                            XposedBridge.log("NotifIconHider: скрываем (before) " + pkg);
                            view.setVisibility(View.GONE);
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
                            XposedBridge.log("NotifIconHider: скрываем (after) " + pkg);
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

        // === Хук 2: setVisibility — ищем в иерархии родителей ===
        Method setVisMeth = findInHierarchy(iconViewClass, "setVisibility", int.class);
        if (setVisMeth != null) {
            XposedBridge.log("NotifIconHider: setVisibility в "
                    + setVisMeth.getDeclaringClass().getName());
            setVisMeth.setAccessible(true);
            XposedBridge.hookMethod(setVisMeth, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!finalIconViewClass.isInstance(param.thisObject)) return;
                    if ((int) param.args[0] != View.VISIBLE) return;
                    View view = (View) param.thisObject;
                    String pkg = (String) view.getTag(TAG_PKG);
                    if (pkg == null) return;
                    if (getBlocked().contains(pkg)) {
                        XposedBridge.log("NotifIconHider: блок setVisibility -> " + pkg);
                        param.setResult(null);
                    }
                }
            });
        }

        // === Хук 3: onAttachedToWindow — ищем в иерархии ===
        Method attachMeth = findInHierarchy(iconViewClass, "onAttachedToWindow");
        if (attachMeth != null) {
            XposedBridge.log("NotifIconHider: onAttachedToWindow в "
                    + attachMeth.getDeclaringClass().getName());
            attachMeth.setAccessible(true);
            XposedBridge.hookMethod(attachMeth, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!finalIconViewClass.isInstance(param.thisObject)) return;
                    View view = (View) param.thisObject;
                    if (cachedCtx == null) {
                        cachedCtx = view.getContext().getApplicationContext();
                    }
                    String pkg = (String) view.getTag(TAG_PKG);
                    if (pkg == null) return;
                    if (getBlocked().contains(pkg)) {
                        view.setVisibility(View.GONE);
                    }
                }
            });
        }

        XposedBridge.log("NotifIconHider: все хуки установлены");
    }

    private static Method findInHierarchy(Class<?> cls, String name, Class<?>... params) {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
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
