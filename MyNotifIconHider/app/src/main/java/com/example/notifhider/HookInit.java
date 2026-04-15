package com.example.notifhider;

import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;
import java.util.Set;

public class HookInit implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) return;

        XposedBridge.log("NotifIconHider: загружаемся в SystemUI");

        // Находим класс StatusBarIconView
        Class<?> iconViewClass;
        try {
            iconViewClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.StatusBarIconView",
                lpparam.classLoader
            );
        } catch (XposedHelpers.ClassNotFoundError e) {
            XposedBridge.log("NotifIconHider: StatusBarIconView не найден: " + e);
            return;
        }

        // Не указываем тип параметра напрямую — на MIUI класс StatusBarIcon
        // находится по другому пути. Перебираем все методы set() через рефлексию.
        boolean hooked = false;
        for (Method method : iconViewClass.getDeclaredMethods()) {
            if (method.getName().equals("set")
                    && method.getParameterCount() == 1
                    && !method.getParameterTypes()[0].isPrimitive()) {

                method.setAccessible(true);
                XposedBridge.log("NotifIconHider: нашли set(" + method.getParameterTypes()[0].getName() + ")");

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object icon = param.args[0];
                        if (icon == null) return;

                        String pkg = null;
                        try {
                            pkg = (String) XposedHelpers.getObjectField(icon, "pkg");
                        } catch (Throwable ignored) {}

                        // Пробуем альтернативные имена поля если "pkg" не нашлось
                        if (pkg == null) {
                            try {
                                pkg = (String) XposedHelpers.getObjectField(icon, "mPackageName");
                            } catch (Throwable ignored) {}
                        }

                        if (pkg == null) return;

                        Set<String> blocked = MySettings.getBlockedNotifications();
                        XposedBridge.log("NotifIconHider: иконка pkg=" + pkg + " заблокирован=" + blocked.contains(pkg));

                        View view = (View) param.thisObject;
                        if (blocked.contains(pkg)) {
                            view.setVisibility(View.GONE);
                        } else if (view.getVisibility() == View.GONE) {
                            view.setVisibility(View.VISIBLE);
                        }
                    }
                });

                hooked = true;
            }
        }

        if (hooked) {
            XposedBridge.log("NotifIconHider: хук установлен успешно");
        } else {
            XposedBridge.log("NotifIconHider: метод set() не найден в StatusBarIconView");
        }
    }
}
