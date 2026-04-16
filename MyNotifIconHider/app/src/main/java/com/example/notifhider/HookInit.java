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

    // Ключ тега для хранения пакета на View
    private static final int TAG_PKG_KEY = "notifhider_pkg".hashCode();

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

        // Хук 1: перехватываем set() чтобы узнать пакет и скрыть иконку
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

                        String pkg = getPkg(icon);
                        if (pkg == null) return;

                        View view = (View) param.thisObject;
                        // Сохраняем пакет в тег View для использования в хуке setVisibility
                        view.setTag(TAG_PKG_KEY, pkg);

                        Set<String> blocked = MySettings.getBlockedNotifications();
                        if (blocked.contains(pkg)) {
                            XposedBridge.log("NotifIconHider: скрываем иконку " + pkg);
                            view.setVisibility(View.GONE);
                        }
                    }
                });
                hooked = true;
            }
        }

        if (!hooked) {
            XposedBridge.log("NotifIconHider: метод set() не найден");
            return;
        }

        // Хук 2: перехватываем setVisibility() — блокируем попытки SystemUI
        // снова показать иконку после нашего скрытия
        try {
            XposedHelpers.findAndHookMethod(iconViewClass, "setVisibility", int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int visibility = (int) param.args[0];
                        // Разрешаем скрывать — блокируем только попытки показать
                        if (visibility == View.GONE || visibility == View.INVISIBLE) return;

                        View view = (View) param.thisObject;
                        String pkg = (String) view.getTag(TAG_PKG_KEY);
                        if (pkg == null) return;

                        Set<String> blocked = MySettings.getBlockedNotifications();
                        if (blocked.contains(pkg)) {
                            XposedBridge.log("NotifIconHider: блокируем setVisibility(VISIBLE) для " + pkg);
                            param.setResult(null); // Не даём показать иконку
                        }
                    }
                });
            XposedBridge.log("NotifIconHider: хук setVisibility установлен");
        } catch (Throwable e) {
            XposedBridge.log("NotifIconHider: ошибка хука setVisibility: " + e);
        }

        XposedBridge.log("NotifIconHider: все хуки установлены");
    }

    private static String getPkg(Object icon) {
        // Пробуем разные имена поля — зависит от версии Android/MIUI
        String[] fieldNames = {"pkg", "mPackageName", "packageName"};
        for (String field : fieldNames) {
            try {
                String val = (String) XposedHelpers.getObjectField(icon, field);
                if (val != null) return val;
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
