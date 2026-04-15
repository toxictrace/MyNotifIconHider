package com.example.notifhider;

import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.Set;

public class HookInit implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) return;

        XposedBridge.log("NotifIconHider: загружаемся в SystemUI");

        // StatusBarIconView.set() вызывается при отображении КАЖДОЙ иконки в статусбаре,
        // включая иконки уведомлений. StatusBarIcon.pkg содержит имя пакета-источника.
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.StatusBarIconView",
                lpparam.classLoader,
                "set",
                "com.android.systemui.statusbar.StatusBarIcon",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object icon = param.args[0];
                        if (icon == null) return;

                        String pkg = (String) XposedHelpers.getObjectField(icon, "pkg");
                        if (pkg == null) return;

                        Set<String> blocked = MySettings.getBlockedNotifications();
                        XposedBridge.log("NotifIconHider: иконка pkg=" + pkg + " заблокирован=" + blocked.contains(pkg));

                        View view = (View) param.thisObject;
                        if (blocked.contains(pkg)) {
                            view.setVisibility(View.GONE);
                        } else {
                            if (view.getVisibility() == View.GONE) {
                                view.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                }
            );
            XposedBridge.log("NotifIconHider: хук StatusBarIconView установлен");
        } catch (Throwable t) {
            XposedBridge.log("NotifIconHider: хук StatusBarIconView не удался: " + t);
        }
    }
}
