package com.custom.lockscreen;

/**
 * Build variant. build.ps1 -Root rewrites this to ROOT = true for LockScreen-Root.apk:
 * root-based protection instead of the accessibility "full protection" service.
 */
final class Flavor {
    static final boolean ROOT = false;

    private Flavor() {}
}
