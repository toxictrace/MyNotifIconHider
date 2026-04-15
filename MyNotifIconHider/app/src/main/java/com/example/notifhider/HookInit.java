package com.example.notifhider;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.Set;

public class HookInit implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        // Хукаем только SystemUI
        if (!lpparam.packageName.equals("com.android.systemui")) return;

        XposedHelpers.findAndHookMethod(
            "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
            lpparam.classLoader,
            "addIcon",
            String.class,   // slot (package name)
            int.class,      // index
            "com.android.systemui.statusbar.StatusBarIcon", // icon
            boolean.class,  // visible
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String pkg = (String) param.args[0];
                    Set<String> blocked = MySettings.getBlockedNotifications();
                    if (blocked.contains(pkg)) {
                        param.setResult(null);
                    }
                }
            });
    }
}
