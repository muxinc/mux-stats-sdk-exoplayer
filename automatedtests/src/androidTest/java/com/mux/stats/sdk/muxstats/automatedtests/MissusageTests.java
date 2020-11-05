package com.mux.stats.sdk.muxstats.automatedtests;

import android.os.Bundle;
import android.os.Handler;

import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.ViewStartEvent;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.http.SimpleHTTPServer;
import com.mux.stats.sdk.muxstats.automatedtests.ui.SimplePlayerBaseActivity;
import com.mux.stats.sdk.muxstats.automatedtests.ui.SimplePlayerTestActivity;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.fail;

public class MissusageTests extends TestBase {

    static final int INIT_MUX_STATS_AFTER = 5000;

    @Before
    public void init(){
        try {
            httpServer = new SimpleHTTPServer(runHttpServerOnPort, bandwidthLimitInBitsPerSecond);
//            httpServer.setSeekLatency(SEEK_PERIOD_IN_MS);
        } catch (IOException e) {
            e.printStackTrace();
            // Failed to start server
            fail("Failed to start HTTP server, why !!!");
        }
        try {
            testActivity = (SimplePlayerTestActivity) getActivityInstance();
        } catch (ClassCastException e) {
            fail("Got wrong activity instance in test init !!!");
        }
        if (testActivity == null) {
            fail("Test activity not found !!!");
        }
//        testScenario = activityRule.getScenario();
    }

    @Test
    public void testLateStatsInit() {
        try {
            // Init test activity but not the Mux stats
            testActivity.runOnUiThread(() -> {
                testActivity.setVideoTitle(currentTestName.getMethodName());
                testActivity.startPlayback();
                pView = testActivity.getPlayerView();
                testMediaSource = testActivity.getTestMediaSource();
            });
            if(!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
                fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
            }
            Thread.sleep(INIT_MUX_STATS_AFTER);
            // Init Mux stats after the playback have started
            testActivity.runOnUiThread(() -> {
                testActivity.initMuxSats();
            });
            Thread.sleep(INIT_MUX_STATS_AFTER * 2);
            // This is initialized with the MuxStats, it need to be called after
            // testActivity.initMuxSats();
            networkRequest = testActivity.getMockNetwork();
            // Check if play, playing and etc events are sent
            int viewstartIndex = networkRequest.getIndexForFirstEvent(ViewStartEvent.TYPE);
            int playIndex = networkRequest.getIndexForFirstEvent(PlayEvent.TYPE);
            int playingIndex = networkRequest.getIndexForFirstEvent(PlayingEvent.TYPE);
            if (viewstartIndex == -1 || playIndex == -1 || playingIndex== -1) {
                fail("Missing playback starting events, viewstartIndex: " + viewstartIndex +
                        ", playIndex: " + playIndex + ", playingIndex: " + playingIndex +
                        " RECEIVED: " + networkRequest.getReceivedEventNames());
            }
            if (!(viewstartIndex < playIndex && playIndex < playingIndex)) {
                fail("Playback starting events not received in correct order, viewstartIndex: "
                        + viewstartIndex + ", playIndex: " + playIndex + ", playingIndex: "
                        + playingIndex + " RECEIVED: " + networkRequest.getReceivedEventNames());
            }
            long playReceivedTime = networkRequest.getCreationTimeForEvent(playIndex) -
                    networkRequest.getCreationTimeForEvent(viewstartIndex);
            if (playReceivedTime > 500) {
                fail("Play event received after: " + playReceivedTime + ", expected less the 500 ms");
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

    }
}
