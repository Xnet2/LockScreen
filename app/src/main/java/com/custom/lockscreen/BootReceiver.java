package com.custom.lockscreen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!Prefs.isEnabled(c)) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) Prefs.setLocked(c, true); // always start locked
        try {
            LockService.start(c);
        } catch (Exception ignored) {
        }
    }
}
