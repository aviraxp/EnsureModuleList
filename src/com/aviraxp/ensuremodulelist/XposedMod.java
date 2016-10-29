package com.aviraxp.ensuremodulelist;

import android.annotation.SuppressLint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam param) throws Throwable {

        if ("de.robv.android.xposed.installer".equals(param.packageName)) {
            Class<?> moduleUtil = XposedHelpers.findClass("de.robv.android.xposed.installer.util.ModuleUtil", param.classLoader);
            XC_MethodHook moduleUtilHook = new ModuleUtilHook();
            XposedBridge.hookAllConstructors(moduleUtil, moduleUtilHook);
            XposedHelpers.findAndHookMethod(moduleUtil, "setModuleEnabled", String.class, boolean.class, moduleUtilHook);
            XposedHelpers.findAndHookMethod(moduleUtil, "isModuleEnabled", String.class, moduleUtilHook);
            XposedHelpers.findAndHookMethod(moduleUtil, "getEnabledModules", moduleUtilHook);
        }
    }

    public static class ModuleUtilHook extends XC_MethodHook {

        @SuppressLint("SdCardPath")
        private static final String MODULES_LIST_FILE = "/data/data/de.robv.android.xposed.installer/conf/modules.list";

        private PackageManager mPm;
        private final Set<String> mModules = new HashSet<String>();

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            String methodName = param.method.getName();
            //XposedBridge.log("before calling ModuleUtil." + methodName);
            if ("setModuleEnabled".equals(methodName)) {
                param.setResult(setModuleEnabled((String) param.args[0], (Boolean) param.args[1]));
            } else if ("isModuleEnabled".equals(methodName)) {
                param.setResult(isModuleEnabled((String) param.args[0]));
            } else if ("getEnabledModules".equals(methodName)) {
                param.setResult(getEnabledModules(param));
            }
        }

        private Void setModuleEnabled(String packageName, boolean enabled) {
            if (enabled) {
                mModules.add(packageName);
            } else {
                mModules.remove(packageName);
            }
            return null;
        }

        private boolean isModuleEnabled(String packageName) {
            return mModules.contains(packageName);
        }

        private List getEnabledModules(MethodHookParam param) {
            List result = new LinkedList();
            for (String packageName : mModules) {
                Object module = XposedHelpers.callMethod(param.thisObject, "getModule", packageName);
                if (module != null) {
                    result.add(module);
                } else {
                    setModuleEnabled(packageName, false);
                }
            }
            return result;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            //.log("after called ModuleUtil." + param.method.getName());
            if (param.method instanceof Constructor) {
                mPm = (PackageManager) XposedHelpers.getObjectField(param.thisObject, "mPm");
                loadModules();
            }
        }

        private void loadModules() {
            try {
                String line;
                BufferedReader reader = new BufferedReader(new FileReader(MODULES_LIST_FILE));
                while ((line = reader.readLine()) != null) {
                    PackageInfo info = mPm.getPackageArchiveInfo(line, 0);
                    if (info != null) {
                        mModules.add(info.packageName);
                    }
                }
                reader.close();
            } catch (IOException e) {
                return;
            }
        }

    }

}
