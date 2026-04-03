package com.motowatchreconnect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            Intent serviceIntent = new Intent(context, WatchReconnectService.class);
            serviceIntent.putExtra(WatchReconnectService.EXTRA_MAC, MainActivity.WATCH_MAC);
            ContextCompat.startForegroundService(context, serviceIntent);
        }
    }
}
