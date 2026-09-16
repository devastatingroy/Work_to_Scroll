package com.roy.worktoscroll;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProductIdentityTest {
    @Test public void keepsExistingWorkToScrollIdentityAndTarget() {
        assertEquals("com.roy.worktoscroll.overlaytest", BuildConfig.APPLICATION_ID);
        assertEquals("WorkToScroll", BuildConfig.PRODUCT_NAME);
        assertEquals("com.xingin.xhs", BuildConfig.TARGET_PACKAGE);
        assertEquals("小红书", BuildConfig.TARGET_NAME);
        assertEquals("未找到小红书，请确认已安装标准版", BuildConfig.TARGET_MISSING_MESSAGE);
    }
}
