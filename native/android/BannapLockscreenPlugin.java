package com.meengook.bannap;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
    name = "BannapLockscreen",
    permissions = {
        @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS })
    }
)
public class BannapLockscreenPlugin extends Plugin {
    private static final String PREFS = "bannap_lockscreen";
    private static final String BOOKS_KEY = "books";

    @PluginMethod
    public void saveBooks(PluginCall call) {
        String books = call.getString("books", "[]");
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(BOOKS_KEY, books).apply();
        call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject result = new JSObject();
        result.put("overlayGranted", Settings.canDrawOverlays(getContext()));
        result.put("enabled", getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("enabled", false));
        call.resolve(result);
    }

    @PluginMethod
    public void enable(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && getPermissionState("notifications") != PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "afterNotificationPermission");
            return;
        }
        openOverlaySettings(call);
    }

    @PermissionCallback
    private void afterNotificationPermission(PluginCall call) {
        openOverlaySettings(call);
    }

    private void openOverlaySettings(PluginCall call) {
        if (!Settings.canDrawOverlays(getContext())) {
            getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", true).apply();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getContext().getPackageName()));
            getActivity().startActivity(intent);
            JSObject result = new JSObject();
            result.put("openedSettings", true);
            call.resolve(result);
            return;
        }
        startService();
        JSObject result = new JSObject();
        result.put("enabled", true);
        call.resolve(result);
    }

    @PluginMethod
    public void start(PluginCall call) {
        if (!Settings.canDrawOverlays(getContext())) {
            call.reject("OVERLAY_PERMISSION_REQUIRED");
            return;
        }
        startService();
        call.resolve();
    }

    @PluginMethod
    public void disable(PluginCall call) {
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", false).apply();
        getContext().stopService(new Intent(getContext(), BannapLockscreenService.class));
        call.resolve();
    }

    private void startService() {
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", true).apply();
        ContextCompat.startForegroundService(getContext(),
            new Intent(getContext(), BannapLockscreenService.class));
    }
}
