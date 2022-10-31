package com.mux.stats.sdk.muxstats.automatedtests;

import static org.junit.Assert.fail;

import com.mux.stats.sdk.core.model.PlayerData;
import com.mux.stats.sdk.core.model.VideoData;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.http.SimpleHTTPServer;
import com.mux.stats.sdk.muxstats.automatedtests.ui.SimplePlayerTestActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

public class LatencyTests extends TestBase {

  boolean playerProgramTiomeFound = false;
  boolean playerManifestNewestTimeFound = false;
  boolean videoHoldbackFound = false;
  boolean videoPartHoldbackFound = false;
  boolean videoTargetDurationFound = false;
  boolean videoPartTargetHoldbackFound = false;

  @Before
  public void init() {
    // For this test we use external live link
    if( currentTestName.getMethodName().equalsIgnoreCase("testLiveStreamMetrics") ) {
      urlToPlay = "https://stream.mux.com/v69RSHhFelSm4701snP22dYz2jICy4E4FUyk02rW4gxRM.m3u8";
    } else {
      urlToPlay = "http://localhost:5000/hls/google_glass/playlist.m3u8";
    }
    super.init();
  }

  private boolean isLatencyMetricsPresent() throws JSONException {
    JSONArray events = networkRequest.getReceivedEventsAsJSON();
    for (int i = 0; i < events.length(); i++) {
      JSONObject event = events.getJSONObject(i);
      if (event.has(PlayerData.PLAYER_PROGRAM_TIME)) {
        playerProgramTiomeFound = true;
      }
      if (event.has(PlayerData.PLAYER_MANIFEST_NEWEST_PROGRAM_TIME)) {
        playerManifestNewestTimeFound = true;
      }
      if (event.has(VideoData.VIDEO_HOLDBACK)) {
        videoHoldbackFound = true;
      }
      if (event.has(VideoData.VIDEO_PART_HOLDBACK)) {
        videoPartHoldbackFound = true;
      }
      if (event.has(VideoData.VIDEO_TARGET_DURATION)) {
        videoTargetDurationFound = true;
      }
      if (event.has(VideoData.VIDEO_PART_TARGET_DURATION)) {
        videoPartTargetHoldbackFound = true;
      }
    }
    return playerProgramTiomeFound && playerManifestNewestTimeFound
        && (videoHoldbackFound || videoPartHoldbackFound) && videoTargetDurationFound
        && videoPartTargetHoldbackFound;
  }

  @Test
  public void testLiveStreamMetrics() {
    try {
      // Test not supported for this flavor
      if (
          BuildConfig.FLAVOR.contains("r2_11_1")
        || BuildConfig.FLAVOR.contains("r2_10_6")
        ) {
        return;
      }
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS * 100)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }
      Thread.sleep(PLAY_PERIOD_IN_MS);
      if (!isLatencyMetricsPresent()) {
        fail("Latency metrics not found on live stream:\n"
            + " playerProgramTimeFound: " + playerProgramTiomeFound
            + " playerManifestNewestTimeFound: " + playerManifestNewestTimeFound
            + " videoHoldbackFound: " + videoHoldbackFound
            + " videoPartHoldbackFound: " + videoPartHoldbackFound
            + " videoTargetDurationFound: " + videoTargetDurationFound
            + " videoPartTargetHoldbackFound: " + videoPartTargetHoldbackFound
        );
      }
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }

  @Test
  public void testVodStreamMetrics() {
    try {
      // Test not supported for this flavor
      if (BuildConfig.FLAVOR.contains("r2_10_6")) {
        return;
      }
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }
      Thread.sleep(PLAY_PERIOD_IN_MS);
      if (isLatencyMetricsPresent()) {
        fail("latency metric found:\n"
          + "playerProgramTiomeFound: " + playerProgramTiomeFound
          + "playerManifestNewestTimeFound: " + playerManifestNewestTimeFound
          + "videoHoldbackFound: " + videoHoldbackFound
          + "videoPartHoldbackFound: " + videoPartHoldbackFound
          + "videoTargetDurationFound: " + videoTargetDurationFound
          + "videoPartTargetHoldbackFound: " + videoPartTargetHoldbackFound
        );
      }
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }
}
