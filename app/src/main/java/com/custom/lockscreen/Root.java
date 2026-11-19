package com.custom.lockscreen;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Runs shell commands as root (via su from Magisk / KernelSU / APatch), always off the main thread. */
final class Root {
    /** null = not checked yet. */
    static volatile Boolean available;

    private static final ExecutorService exec = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private Root() {}

    /** Checks for root (may show the root manager's permission prompt), result on the main thread. */
    static void check(Consumer<Boolean> result) {
        exec.execute(() -> {
            boolean ok = output("id").contains("uid=0");
            available = ok;
            if (result != null) main.post(() -> result.accept(ok));
        });
    }

    /** Runs the command as root in the background (checks for root first if needed). */
    static void run(String cmd) {
        run(cmd, null);
    }

    static void run(String cmd, Runnable done) {
        exec.execute(() -> {
            if (available == null) available = output("id").contains("uid=0");
            if (Boolean.TRUE.equals(available)) exitCode(cmd);
            if (done != null) main.post(done);
        });
    }

    private static int exitCode(String cmd) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            drain(p);
            return p.waitFor();
        } catch (Exception e) {
            return -1;
        }
    }

    private static String output(String cmd) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            String out = drain(p);
            p.waitFor();
            return out;
        } catch (Exception e) {
            return "";
        }
    }

    private static String drain(Process p) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
