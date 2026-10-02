package com.cms.util;

import java.util.List;
import java.util.Set;

/**
 * Public demo mode, switched on with the environment variable (or JVM option) CMS_DEMO_MODE=true.
 * Off by default, so local and normal installations behave as before. In demo mode the three demo
 * logins cannot be locked out by visitors (no password changes, no editing, deactivating or
 * deleting them), uploads are smaller, and every page shows a banner. The data is reset nightly.
 */
public final class DemoMode {

    /** Username, password, role label: shown on the login page in demo mode. */
    public static final List<String[]> ACCOUNTS = List.of(
            new String[] { "ADMIN", "ADMIN123", "Administrator" },
            new String[] { "TEACHER1", "Teacher123", "Teacher" },
            new String[] { "BCSF22M512", "Usman123", "Student" });

    private static final Set<String> PROTECTED = Set.of("ADMIN", "TEACHER1", "BCSF22M512");

    /** Largest upload in demo mode (the demo host's disk is small and temporary). */
    public static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;

    public static final String BLOCKED_MESSAGE =
            "This is a public demo: the demo accounts (ADMIN, TEACHER1, BCSF22M512) cannot be edited, deactivated, "
                    + "deleted or given a new password, so every visitor can log in. Everything else can be tried; the data resets every night.";

    private DemoMode() {
    }

    public static boolean isOn() {
        String v = System.getProperty("CMS_DEMO_MODE");
        if (v == null)
            v = System.getenv("CMS_DEMO_MODE");
        return v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes"));
    }

    /** True in demo mode for one of the three demo logins. */
    public static boolean isProtected(String username) {
        return isOn() && username != null && PROTECTED.contains(username.toUpperCase());
    }

    /** Upload limit: 10 MB normally, 2 MB in demo mode. */
    public static long maxUploadBytes() {
        return isOn() ? MAX_UPLOAD_BYTES : UploadStore.MAX_BYTES;
    }
}
