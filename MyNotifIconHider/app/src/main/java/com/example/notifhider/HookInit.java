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
    private static volatile long lastRead = 0; // 0 = ни разу не читали успешно
    private static volatile Context cachedCtx = null;

    /**
     * Читает ContentProvider.
     * Возвращает null если провайдер ещё недоступен (приложение не запущено).
     * Возвращает пустой Set если провайдер работает, но список пуст.
     */
    private static Set<String> readFromProvider(Context ctx) {
        try {
            Cursor cursor = ctx.getContentResolver().query(
                    PREFS_URI, null, null, null, null);
            if (cursor == null) {
                XposedBridge.log("NotifIconHider: провайдер недоступен (cursor=null)");
                return null;
            }
            Set<String> result = new HashSet<>();
            try {
                while (cursor.moveToNext()) result.add(cursor.getString(0));
            } finally {
                cursor.close();
            }
            XposedBridge.log("NotifIconHider: заблокировано: " + result);
            return result;
        } catch (Throwable t) {
            XposedBridge.log("NotifIconHider: ошибка ContentProvider: " + t);
            return null;
        }
    }

    private static Set<String> getBlocked() {
        long now = System.currentTimeMillis();
        if (cachedCtx != null && now - lastRead > 1000) {
            Set<String> fresh = readFromProvider(cachedCtx);
            if (fresh != null) {
                blockedCache = fresh;
                lastRead = now; // обновляем только при успешном чтении
            }
            // fresh == null → провайдер недоступен → lastRead остаётся → повтор при следующем вызове
        }
        return blockedCache;
    }

    /**
     * Если при первом вызове провайдер недоступен — планирует повторную попытку
     * скрытия иконки через postDelayed с экспоненциальным откатом (1с, 2с, 4с … 32с).
     */
    private static void scheduleRetryHide(View view, String pkg, int delayMs) {
        if (delayMs > 32000) return;
        view.postDelayed(() -> {
            Set<String> blocked = getBlocked();
            if (blocked.contains(pkg)) {
                XposedBridge.log("NotifIconHider: скрываем (retry) " + pkg);
                view.setVisibility(View.GONE);
            } else if (lastRead == 0) {
                // Провайдер ещё не поднялся — пробуем снова
                scheduleRetryHide(view, pkg, delayMs * 2);
            }
        }, delayMs);
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
                XposedBridge.log("NotifIconHider: хукаем set("
                        + m.getParameterTypes()[0].getName() + ")");

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        if (cachedCtx == null)
                            cachedCtx = view.getContext().getApplicationContext();

                        Object icon = param.args[0];
                        if (icon == null) return;
                        String pkg = getPkg(icon);
                        if (pkg == null) return;
                        view.setTag(TAG_PKG, pkg);

                        Set<String> blocked = getBlocked();
                        if (blocked.contains(pkg)) {
                            XposedBridge.log("NotifIconHider: скрываем " + pkg);
                            view.setVisibility(View.GONE);
                        } else if (lastRead == 0) {
                            // Провайдер ещё не готов — планируем повторную попытку
                            scheduleRetryHide(view, pkg, 1000);
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

        // === Хук 2: setVisibility — блокируем VISIBLE для скрытых иконок ===
        Method setVisMeth = findInHierarchy(iconViewClass, "setVisibility", int.class);
        if (setVisMeth != null) {
            XposedBridge.log("NotifIconHider: hookSetVisibility в "
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
                        param.setResult(null); // не даём сделать VISIBLE
                    }
                }
            });
        }

        // === Хук 3: onAttachedToWindow — скрываем при повторном добавлении в иерархию ===
        Method attachMeth = findInHierarchy(iconViewClass, "onAttachedToWindow");
        if (attachMeth != null) {
            XposedBridge.log("NotifIconHider: hookOnAttachedToWindow в "
                    + attachMeth.getDeclaringClass().getName());
            attachMeth.setAccessible(true);
            XposedBridge.hookMethod(attachMeth, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!finalIconViewClass.isInstance(param.thisObject)) return;
                    View view = (View) param.thisObject;
                    if (cachedCtx == null)
                        cachedCtx = view.getContext().getApplicationContext();
                    String pkg = (String) view.getTag(TAG_PKG);
                    if (pkg == null) return;
                    if (getBlocked().contains(pkg)) view.setVisibility(View.GONE);
                }
            });
        }

        XposedBridge.log("NotifIconHider: все хуки установлены");
    }

    private static Method findInHierarchy(Class<?> cls, String name, Class<?>... params) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredMethod(name, params); }
            catch (NoSuchMethodException ignored) {}
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
