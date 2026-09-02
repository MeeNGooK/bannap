package com.meengook.bannap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BannapNotificationActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (BannapLockscreenService.ACTION_STOP.equals(intent.getAction())) {
            context.getSharedPreferences(BannapLockscreenService.PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            context.stopService(new Intent(context, BannapLockscreenService.class));
        }
    }
}
