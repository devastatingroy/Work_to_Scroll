package com.roy.worktoscroll;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ProductIdentityTest {
    @Test public void usesIndependentWorkToWatchIdentityAndYouTubeTarget() {
        assertEquals("com.roy.worktowatch", BuildConfig.APPLICATION_ID);
        assertEquals("WorkToWatch", BuildConfig.PRODUCT_NAME);
        assertEquals("com.google.android.youtube", BuildConfig.TARGET_PACKAGE);
        assertEquals("YouTube", BuildConfig.TARGET_NAME);
        assertEquals("未找到YouTube，请确认已安装官方 Android App", BuildConfig.TARGET_MISSING_MESSAGE);
        assertNotEquals("com.roy.worktoscroll.overlaytest", BuildConfig.APPLICATION_ID);
        assertNotEquals("com.xingin.xhs", BuildConfig.TARGET_PACKAGE);
    }
}
