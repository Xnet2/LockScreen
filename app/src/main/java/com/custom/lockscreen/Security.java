package com.custom.lockscreen;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Stores the unlock secret only as a salted PBKDF2 hash, never in plain text.
 * Patterns are stored as the sequence of dot indexes, e.g. "0125".
 */
final class Security {
    private static final int ITERATIONS = 12000;

    private Security() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("lock_security", Context.MODE_PRIVATE);
    }

    static LockType type(Context c) {
        try {
            return LockType.valueOf(sp(c).getString("type", LockType.NONE.name()));
        } catch (Exception e) {
            return LockType.NONE;
        }
    }

    static int pinLength(Context c) { return sp(c).getInt("pin_len", 0); }

    static void set(Context c, LockType type, String secret) {
        SharedPreferences.Editor e = sp(c).edit().clear();
        if (type != LockType.NONE && secret != null && !secret.isEmpty()) {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            e.putString("type", type.name())
                    .putString("salt", enc(salt))
                    .putString("hash", enc(hash(secret, salt)));
            if (type == LockType.PIN) e.putInt("pin_len", secret.length());
        } else {
            e.putString("type", LockType.NONE.name());
        }
        e.commit();
    }

    static boolean verify(Context c, String secret) {
        SharedPreferences p = sp(c);
        String salt = p.getString("salt", null);
        String h = p.getString("hash", null);
        if (salt == null || h == null) return false;
        return MessageDigest.isEqual(hash(secret, dec(salt)), dec(h));
    }

    /** After every 5 wrong attempts: 30s, 60s, 2m, 4m ... up to 16 minutes. */
    static void onFailure(Context c) {
        SharedPreferences p = sp(c);
        int failed = p.getInt("failed", 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt("failed", failed);
        if (failed % 5 == 0) {
            int step = Math.min(Math.max(failed / 5 - 1, 0), 5);
            e.putLong("lockout_until", System.currentTimeMillis() + 30_000L * (1L << step));
        }
        e.commit();
    }

    static void onSuccess(Context c) {
        sp(c).edit().putInt("failed", 0).putLong("lockout_until", 0).apply();
    }

    static long lockoutRemainingMs(Context c) {
        return Math.max(0, sp(c).getLong("lockout_until", 0) - System.currentTimeMillis());
    }

    private static byte[] hash(String secret, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String enc(byte[] b) { return Base64.encodeToString(b, Base64.NO_WRAP); }
    private static byte[] dec(String s) { return Base64.decode(s, Base64.NO_WRAP); }
}
