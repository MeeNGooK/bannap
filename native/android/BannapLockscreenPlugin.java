package com.meengook.bannap;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "BannapLockscreen")
public class BannapLockscreenPlugin extends Plugin {
    private static final String PREFS = "bannap_lockscreen";
    private static final String BOOKS_KEY = "books";

    @PluginMethod
    public void saveBooks(PluginCall call) {
        String books = call.getString("books", "[]");
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(BOOKS_KEY, books).apply();
        BannapWidgetProvider.refreshAll(getContext());
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
        try {
            startService();
            JSObject result = new JSObject();
            result.put("enabled", true);
            call.resolve(result);
        } catch (Exception error) {
            call.reject("LOCKSCREEN_SERVICE_START_FAILED: " + error.getMessage());
        }
    }

    @PluginMethod
    public void start(PluginCall call) {
        if (!Settings.canDrawOverlays(getContext())) {
            call.reject("OVERLAY_PERMISSION_REQUIRED");
            return;
        }
        try {
            startService();
            call.resolve();
        } catch (Exception error) {
            call.reject("LOCKSCREEN_SERVICE_START_FAILED: " + error.getMessage());
        }
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
