package com.remotemix.catalog;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;

/** Runs as the ADB shell user through app_process. It only reads launcher metadata. */
public final class RemoteAppCatalogProbe {
    private static final String PREFIX = "RMAPP\t";

    public static void main(String[] args) {
        try {
            Looper.prepareMainLooper();
            PackageManager pm = systemContext().getPackageManager();
            LinkedHashMap<String, ResolveInfo> apps = new LinkedHashMap<>();
            collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, apps);
            collect(pm, Intent.CATEGORY_LAUNCHER, apps);
            System.out.println("RMAPP_READY\t" + apps.size());
            for (ResolveInfo info : apps.values()) {
                try {
                    ActivityInfo activity = info.activityInfo;
                    String label;
                    try {
                        label = String.valueOf(info.loadLabel(pm)).trim();
                    } catch (Throwable error) {
                        label = activity.packageName;
                    }
                    if (label.isEmpty()) label = activity.packageName;
                    JSONObject row = new JSONObject();
                    row.put("package", activity.packageName);
                    row.put("component", activity.packageName + "/" + activity.name);
                    row.put("label", label);
                    String icon = "";
                    try {
                        icon = iconBase64(info.loadIcon(pm));
                    } catch (Throwable ignored) {
                        // Preserve the real label and launch component even if an icon is broken.
                    }
                    row.put("icon", icon);
                    System.out.println(PREFIX + row);
                } catch (Throwable error) {
                    System.err.println("RMAPP_ITEM_ERROR " + error.getClass().getSimpleName());
                }
            }
            System.out.flush();
        } catch (Throwable error) {
            System.err.println("RMAPP_ERROR " + error.getClass().getSimpleName() + ": " + error.getMessage());
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void collect(PackageManager pm, String category,
                                LinkedHashMap<String, ResolveInfo> apps) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo info : found) {
            if (info.activityInfo != null) apps.putIfAbsent(info.activityInfo.packageName, info);
        }
    }

    private static Context systemContext() throws Exception {
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Constructor<?> constructor = activityThread.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object thread = constructor.newInstance();
        Field current = activityThread.getDeclaredField("sCurrentActivityThread");
        current.setAccessible(true);
        current.set(null, thread);
        Field systemThread = activityThread.getDeclaredField("mSystemThread");
        systemThread.setAccessible(true);
        systemThread.setBoolean(thread, true);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                Class<?> controllerClass = Class.forName("android.app.ConfigurationController");
                Class<?> internalClass = Class.forName("android.app.ActivityThreadInternal");
                Constructor<?> controllerConstructor = controllerClass.getDeclaredConstructor(internalClass);
                controllerConstructor.setAccessible(true);
                Field configuration = activityThread.getDeclaredField("mConfigurationController");
                configuration.setAccessible(true);
                configuration.set(thread, controllerConstructor.newInstance(thread));
            } catch (Throwable ignored) {
                // Some Android versions do not expose this configuration helper.
            }
        }
        Method context = activityThread.getDeclaredMethod("getSystemContext");
        context.setAccessible(true);
        Context systemContext = (Context) context.invoke(thread);
        Application app = Instrumentation.newApplication(Application.class, systemContext);
        Field initial = activityThread.getDeclaredField("mInitialApplication");
        initial.setAccessible(true);
        initial.set(thread, app);
        return systemContext;
    }

    private static String iconBase64(Drawable icon) {
        if (icon == null) return "";
        int size = 64;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        icon.setBounds(0, 0, size, size);
        icon.draw(canvas);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        bitmap.recycle();
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
    }
}
