package com.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cms.util.DemoMode;
import com.cms.util.UploadStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DemoModeTest {

    @AfterEach
    void off() {
        System.clearProperty("CMS_DEMO_MODE");
    }

    @Test
    void offByDefaultSoNothingIsProtected() {
        System.clearProperty("CMS_DEMO_MODE");
        if (System.getenv("CMS_DEMO_MODE") != null)
            return; // a machine running with the variable set: nothing to check here
        assertFalse(DemoMode.isOn());
        assertFalse(DemoMode.isProtected("ADMIN"));
        assertEquals(UploadStore.MAX_BYTES, DemoMode.maxUploadBytes());
    }

    @Test
    void onlyTheThreeDemoLoginsAreProtected() {
        System.setProperty("CMS_DEMO_MODE", "true");
        assertTrue(DemoMode.isOn());
        assertTrue(DemoMode.isProtected("ADMIN"));
        assertTrue(DemoMode.isProtected("teacher1")); // usernames are case-insensitive
        assertTrue(DemoMode.isProtected("BCSF22M512"));
        assertFalse(DemoMode.isProtected("TCH-1002"));
        assertFalse(DemoMode.isProtected(null));
        assertEquals(2L * 1024 * 1024, DemoMode.maxUploadBytes());
        assertEquals(3, DemoMode.ACCOUNTS.size());
    }

    @Test
    void onlyClearValuesSwitchItOn() {
        System.setProperty("CMS_DEMO_MODE", "no");
        assertFalse(DemoMode.isOn());
        System.setProperty("CMS_DEMO_MODE", "1");
        assertTrue(DemoMode.isOn());
    }
}
