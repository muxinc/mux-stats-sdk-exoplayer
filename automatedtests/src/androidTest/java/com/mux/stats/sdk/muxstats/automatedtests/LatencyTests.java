package com.mux.stats.sdk.muxstats.automatedtests;

import static org.junit.Assert.fail;

import com.mux.stats.sdk.muxstats.automatedtests.mockup.http.SimpleHTTPServer;
import com.mux.stats.sdk.muxstats.automatedtests.ui.SimplePlayerTestActivity;
import org.junit.Before;
import org.junit.Test;

public class LatencyTests extends TestBase {

  @Before
  public void init() {
    // For this test we use external live link
    urlToPlay = "https://stream.mux.com/v69RSHhFelSm4701snP22dYz2jICy4E4FUyk02rW4gxRM.m3u8";
    try {
      testActivity = (SimplePlayerTestActivity) getActivityInstance();
    } catch (ClassCastException e) {
      fail("Got wrong activity instance in test init !!!");
    }
    if (testActivity == null) {
      fail("Test activity not found !!!");
    }
    testActivityFinished = false;
    testActivity.runOnUiThread(() -> {
      testActivity.setVideoTitle(BuildConfig.FLAVOR + "-" + currentTestName.getMethodName());
      testActivity.setUrlToPlay(urlToPlay);
      testActivity.setPlayWhenReady(playWhenReady);
      testActivity.initMuxSats();
      testActivity.setPlaybackStartPosition(playbackStartPosition);
      testActivity.startPlayback();
      pView = testActivity.getPlayerView();
      testMediaSource = testActivity.getTestMediaSource();
      networkRequest = testActivity.getMockNetwork();
    });
  }

  @Test
  public void testLivePlayhead() {
    try {
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }
      Thread.sleep(PLAY_PERIOD_IN_MS * 3);
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }

}
