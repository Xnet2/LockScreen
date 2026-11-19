package com.custom.lockscreen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.telephony.TelephonyManager;

/**
 * Always-running foreground service: shows the lock as soon as the screen turns
 * OFF, so it is already in place (no flash of the home screen) when it turns on.
 */
public class LockService extends Service {

    static final String ACTION_LOCK_NOW = "com.custom.lockscreen.LOCK_NOW";
    private static final String CHANNEL = "lock_service";

    static void start(Context c) {
        c.startForegroundService(new Intent(c, LockService.class));
    }

    static void lockNow(Context c) {
        c.startForegroundService(new Intent(c, LockService.class).setAction(ACTION_LOCK_NOW));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, LockService.class));
    }

    private LockOverlay overlay;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                // During a call the proximity sensor turns the screen off; don't lock then.
                if (!overlay.isInCall()) overlay.lock();
            } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
                overlay.onScreenOn();
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(a)) {
                onPhoneState(i.getStringExtra(TelephonyManager.EXTRA_STATE));
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(1, buildNotification());
        overlay = LockOverlay.get(this);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        registerReceiver(receiver, f);
        if (Prefs.isEnabled(this) && Prefs.isLocked(this)) overlay.lock();
        else RootGuard.clearShadeBlock(); // fail-safe: never leave the shade disabled after a crash
        RootGuard.applySafeMode(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.isEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_LOCK_NOW.equals(intent.getAction())) overlay.lock();
        return START_STICKY;
    }

    private void onPhoneState(String state) {
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)
                || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            overlay.onCallStarted(); // let the dialer / incoming-call screen through
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            overlay.onCallEnded(); // re-lock if we were locked before the call
        }
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        if (overlay != null) overlay.destroy();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "שירות מסך נעילה",
                NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_lock)
                .setContentTitle("מסך הנעילה פעיל")
                .setContentText("הקש לפתיחת ההגדרות")
                .setContentIntent(pi)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }
}
