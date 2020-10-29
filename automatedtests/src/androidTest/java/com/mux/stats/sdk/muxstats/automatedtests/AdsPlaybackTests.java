package com.mux.stats.sdk.muxstats.automatedtests;

import android.os.Bundle;

import com.mux.stats.sdk.muxstats.automatedtests.ui.SimplePlayerBaseActivity;

import org.junit.Test;

public class AdsPlaybackTests extends TestBase {

    public Bundle getActivityOptions() {
        Bundle b = new Bundle();
        b.putInt(SimplePlayerBaseActivity.PLAYBACK_URL_KEY,
                SimplePlayerBaseActivity.PLAY_PRE_ROLL_AND_BUMPER_SAMPLE);
        return b;
    }

    @Test
    public void testPreRollAndBumperAds() {
        try {
            // Wait to see if ads will play
            Thread.sleep(1000000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
