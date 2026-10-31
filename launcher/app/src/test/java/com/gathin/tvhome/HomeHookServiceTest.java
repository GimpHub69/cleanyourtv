package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeHookServiceTest {
    @Test public void onlyAmazonsHomeScreenCounts() {
        assertTrue(HomeHookService.isAmazonHome("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.HomeActivity_vNext"));
        assertFalse(HomeHookService.isAmazonHome("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity"));
        assertFalse(HomeHookService.isAmazonHome("tv.dumbtv.home", "com.amazon.tv.launcher.ui.HomeActivity_vNext"));
        assertFalse(HomeHookService.isAmazonHome(null, "com.amazon.tv.launcher.ui.HomeActivity_vNext"));
        assertFalse(HomeHookService.isAmazonHome("com.amazon.tv.launcher", null));
    }

    @Test public void anOrdinaryAppCancelsAQueuedLaunch() {
        assertTrue(HomeHookService.leftFor("com.netflix.ninja"));
        assertFalse(HomeHookService.leftFor("com.amazon.tv.launcher"));
        assertFalse(HomeHookService.leftFor("com.android.systemui"));
        assertFalse(HomeHookService.leftFor("android"));
        assertFalse(HomeHookService.leftFor(null));
    }
}
