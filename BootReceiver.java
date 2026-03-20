package com.paybox.soundbox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            SharedPreferences prefs = context.getSharedPreferences("paybox", Context.MODE_PRIVATE);
            boolean setupDone = prefs.getBoolean("setup_done", false);
            boolean serviceOn = prefs.getBoolean("service_on", true);

            if (setupDone && serviceOn) {
                Intent serviceIntent = new Intent(context, PayBoxService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}
