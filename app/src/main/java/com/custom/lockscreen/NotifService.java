package com.custom.lockscreen;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the phone's notifications (with the user's "notification access" permission)
 * and groups them per app for the lock screen. Runs in the same process as the lock.
 */
public class NotifService extends NotificationListenerService {

    /** All current notifications of one app, newest first. */
    static final class Group {
        String pkg;
        CharSequence app;
        Drawable icon;
        final List<StatusBarNotification> items = new ArrayList<>();
        long when;
        CharSequence title, text;
        PendingIntent intent;
        String latestKey;
        boolean autoCancel;
    }

    private static NotifService instance;
    private static Runnable listener;
    private static List<Group> groups = new ArrayList<>();
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private final Map<String, CharSequence> labelCache = new HashMap<>();

    static boolean isGranted(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(new ComponentName(c, NotifService.class));
    }

    static void setListener(Runnable r) { listener = r; }

    static List<Group> groups() { return groups; }

    static void dismiss(Group g) {
        NotifService s = instance;
        if (s == null) return;
        for (StatusBarNotification sbn : g.items) {
            if (sbn.isClearable()) {
                try {
                    s.cancelNotification(sbn.getKey());
                } catch (Exception ignored) {
                }
            }
        }
    }

    static void cancel(String key) {
        NotifService s = instance;
        if (s == null || key == null) return;
        try {
            s.cancelNotification(key);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        refresh();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        groups = new ArrayList<>();
        fire();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        refresh();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
        refresh();
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
        refresh();
    }

    private void refresh() {
        StatusBarNotification[] all;
        try {
            all = getActiveNotifications();
        } catch (Exception e) {
            all = null;
        }
        if (all == null) all = new StatusBarNotification[0];
        Arrays.sort(all, (a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        RankingMap rm = null;
        try {
            rm = getCurrentRanking();
        } catch (Exception ignored) {
        }

        LinkedHashMap<String, Group> map = new LinkedHashMap<>();
        for (StatusBarNotification sbn : all) {
            if (!include(sbn, rm)) continue;
            Group g = map.get(sbn.getPackageName());
            if (g == null) {
                g = new Group();
                g.pkg = sbn.getPackageName();
                g.app = label(g.pkg);
                g.icon = icon(sbn);
                map.put(g.pkg, g);
            }
            g.items.add(sbn);
        }
        for (Group g : map.values()) {
            StatusBarNotification latest = g.items.get(0);
            Notification n = latest.getNotification();
            Bundle ex = n.extras;
            g.when = latest.getPostTime();
            g.title = firstNonEmpty(ex.getCharSequence(Notification.EXTRA_TITLE),
                    ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
            g.text = firstNonEmpty(ex.getCharSequence(Notification.EXTRA_TEXT),
                    ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
            g.intent = n.contentIntent;
            g.latestKey = latest.getKey();
            g.autoCancel = (n.flags & Notification.FLAG_AUTO_CANCEL) != 0;
        }
        groups = new ArrayList<>(map.values());
        fire();
    }

    private boolean include(StatusBarNotification sbn, RankingMap rm) {
        if (sbn.getPackageName().equals(getPackageName())) return false;
        Notification n = sbn.getNotification();
        int skip = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE
                | Notification.FLAG_GROUP_SUMMARY;
        if ((n.flags & skip) != 0) return false;
        if (n.visibility == Notification.VISIBILITY_SECRET) return false;
        if (rm != null) {
            Ranking r = new Ranking();
            if (rm.getRanking(sbn.getKey(), r) && r.getImportance() < NotificationManager.IMPORTANCE_LOW) return false;
        }
        Bundle ex = n.extras;
        return !isEmpty(ex.getCharSequence(Notification.EXTRA_TITLE))
                || !isEmpty(ex.getCharSequence(Notification.EXTRA_TEXT));
    }

    private CharSequence label(String pkg) {
        CharSequence l = labelCache.get(pkg);
        if (l != null) return l;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            l = pm.getApplicationLabel(ai);
        } catch (Exception e) {
            l = pkg;
        }
        labelCache.put(pkg, l);
        return l;
    }

    private Drawable icon(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (iconCache.containsKey(pkg)) return iconCache.get(pkg);
        Drawable d = null;
        try {
            d = getPackageManager().getApplicationIcon(pkg);
        } catch (Exception ignored) {
        }
        if (d == null) {
            try {
                d = sbn.getNotification().getSmallIcon().loadDrawable(this);
            } catch (Exception ignored) {
            }
        }
        iconCache.put(pkg, d);
        return d;
    }

    private static boolean isEmpty(CharSequence s) {
        return s == null || s.toString().trim().isEmpty();
    }

    private static CharSequence firstNonEmpty(CharSequence a, CharSequence b) {
        if (!isEmpty(a)) return a.toString();
        if (!isEmpty(b)) return b.toString();
        return null;
    }

    private static void fire() {
        Runnable r = listener;
        if (r != null) r.run();
    }
}
