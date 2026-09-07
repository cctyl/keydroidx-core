package io.github.cctyl.nokia.common.permission;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class KeydroidxPermissionManagerTest {

    @Test
    public void testPermissionConstants() {
        assertEquals("com.unisoc.permission.CTA_QUERY_ALL_PACKAGES", KeydroidxPermissionManager.PERMISSION_CTA_QUERY_ALL_PACKAGES);
        assertEquals("android.permission.GET_INSTALLED_APPS", KeydroidxPermissionManager.PERMISSION_GET_INSTALLED_APPS);
    }
}
