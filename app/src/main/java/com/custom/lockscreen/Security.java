package com.custom.lockscreen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Stores the unlock secret only as a salted PBKDF2-SHA256 hash, never in plain text.
 * Patterns are stored as the sequence of dot indexes, e.g. "0125".
 * Hashing is deliberately slow (to resist brute force), so it runs off the main thread.
 */
final class Security {
    /** Current work factor. Older hashes (12,000) are upgraded on the next successful unlock. */
    private static final int ITERATIONS = 60000;
    private static final int LEGACY_ITERATIONS = 12000;

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private Security() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("lock_security", Context.MODE_PRIVATE);
    }

    /** Failed-attempt counters live apart from the secret, so changing the secret can't reset a lockout. */
    private static SharedPreferences guard(Context c) {
        return c.getSharedPreferences("lock_attempts", Context.MODE_PRIVATE);
    }

    static LockType type(Context c) {
        try {
            return LockType.valueOf(sp(c).getString("type", LockType.NONE.name()));
        } catch (Exception e) {
            return LockType.NONE;
        }
    }

    static int pinLength(Context c) { return sp(c).getInt("pin_len", 0); }

    /** Saves a new secret (slow hash on a background thread), then runs done on the main thread. */
    static void setAsync(Context c, LockType type, String secret, Runnable done) {
        Context app = c.getApplicationContext();
        worker.execute(() -> {
            set(app, type, secret);
            if (done != null) main.post(done);
        });
    }

    static void set(Context c, LockType type, String secret) {
        SharedPreferences.Editor e = sp(c).edit().clear();
        if (type != LockType.NONE && secret != null && !secret.isEmpty()) {
            byte[] salt = new byte[32];
            new SecureRandom().nextBytes(salt);
            e.putString("type", type.name())
                    .putString("salt", enc(salt))
                    .putInt("iter", ITERATIONS)
                    .putString("hash", enc(hash(secret, salt, ITERATIONS)));
            if (type == LockType.PIN) e.putInt("pin_len", secret.length());
        } else {
            e.putString("type", LockType.NONE.name());
        }
        e.commit();
    }

    /** Checks the secret on a background thread; result (true = correct) is delivered on the main thread. */
    static void verifyAsync(Context c, String secret, Consumer<Boolean> result) {
        Context app = c.getApplicationContext();
        worker.execute(() -> {
            boolean ok = verify(app, secret);
            main.post(() -> result.accept(ok));
        });
    }

    static boolean verify(Context c, String secret) {
        SharedPreferences p = sp(c);
        String salt = p.getString("salt", null);
        String h = p.getString("hash", null);
        if (salt == null || h == null) return false;
        int iter = p.getInt("iter", LEGACY_ITERATIONS);
        boolean ok = MessageDigest.isEqual(hash(secret, dec(salt), iter), dec(h));
        if (ok && iter < ITERATIONS) set(c, type(c), secret); // upgrade to the stronger hash
        return ok;
    }

    /** After every 5 wrong attempts: 30s, 60s, 2m, 4m ... up to 16 minutes. */
    static void onFailure(Context c) {
        SharedPreferences p = guard(c);
        int failed = p.getInt("failed", 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt("failed", failed);
        if (failed % 5 == 0) {
            int step = Math.min(Math.max(failed / 5 - 1, 0), 5);
            long ms = 30_000L * (1L << step);
            // Wall clock AND time-since-boot: changing the phone's clock doesn't end a lockout early.
            e.putLong("lockout_until", System.currentTimeMillis() + ms)
                    .putLong("lockout_until_rt", SystemClock.elapsedRealtime() + ms);
        }
        e.commit();
    }

    static void onSuccess(Context c) {
        guard(c).edit().clear().apply();
    }

    static long lockoutRemainingMs(Context c) {
        SharedPreferences p = guard(c);
        long wall = p.getLong("lockout_until", 0) - System.currentTimeMillis();
        long rt = p.getLong("lockout_until_rt", 0) - SystemClock.elapsedRealtime();
        // elapsedRealtime resets on reboot; only trust it when it's plausible.
        if (rt > 16 * 60_000L) rt = 0;
        return Math.max(0, Math.max(wall, rt));
    }

    private static byte[] hash(String secret, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, 256);
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String enc(byte[] b) { return Base64.encodeToString(b, Base64.NO_WRAP); }
    private static byte[] dec(String s) { return Base64.decode(s, Base64.NO_WRAP); }
}
