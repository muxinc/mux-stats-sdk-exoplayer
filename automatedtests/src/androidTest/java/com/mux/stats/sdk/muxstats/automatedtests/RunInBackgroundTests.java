package com.mux.stats.sdk.muxstats.automatedtests;

import org.junit.Test;

public class RunInBackgroundTests extends TestBase {

    @Test
    public void testBackgroundAudioPlayback() {
        try {
            testActivity.waitForActivityToInitialize();
            backgroundActivity();
            testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS);
            Thread.sleep(10000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
