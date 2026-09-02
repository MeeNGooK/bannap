package com.meengook.bannap;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(name = "BannapLockscreen", permissions = {
    @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS })
})
public class BannapLockscreenPlugin extends Plugin {
    private static final String PREFS = "bannap_lockscreen";

    @PluginMethod
    public void saveBooks(PluginCall call) {
        String books = call.getString("books", "[]");
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("books", books).apply();
        BannapWidgetProvider.refreshAll(getContext());
        if (getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)) startService();
        call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject result = new JSObject();
        result.put("overlayGranted", true);
        result.put("enabled", getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false));
        call.resolve(result);
    }

    @PluginMethod
    public void enable(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && getPermissionState("notifications") != PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "afterNotificationPermission");
            return;
        }
        enableNotifications(call);
    }

    @PermissionCallback
    private void afterNotificationPermission(PluginCall call) { enableNotifications(call); }

    @PluginMethod
    public void start(PluginCall call) { enableNotifications(call); }

    private void enableNotifications(PluginCall call) {
        try {
            getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
            startService();
            JSObject result = new JSObject(); result.put("enabled", true); call.resolve(result);
        } catch (Exception error) { call.reject("NOTIFICATION_SERVICE_START_FAILED: " + error.getMessage()); }
    }

    @PluginMethod
    public void disable(PluginCall call) {
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
        getContext().stopService(new Intent(getContext(), BannapLockscreenService.class));
        call.resolve();
    }

    private void startService() {
        ContextCompat.startForegroundService(getContext(), new Intent(getContext(), BannapLockscreenService.class));
    }
}
