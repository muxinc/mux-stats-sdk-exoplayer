package com.mux.stats.sdk.muxstats.automatedtests;

import static org.junit.Assert.fail;

import android.util.Log;
import com.mux.stats.sdk.core.events.playback.RequestCompleted;
import com.mux.stats.sdk.muxstats.MuxStats;
import com.mux.stats.sdk.muxstats.MuxStatsExoPlayer;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Request;

public class BandwidthMetricTests extends TestBase {

  static long[] manifestDelayList = {100, 50, 150, 50, 350, 200, 250, 100, 40, 30, 25, 160, 79,
      120, 160, 180, 190, 320, 480, 760, 920};

  @Before
  public void init() {
    urlToPlay = "http://localhost:5000/hls/google_glass/playlist.m3u8";
    bandwidthLimitInBitsPerSecond = 12000000;
    super.init();
    httpServer.setHLSManifestDelay(manifestDelayList[0]);
  }

  @Test
  public void testBandwidthMetrics() {
    try {
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }
      MuxStatsExoPlayer muxStats = testActivity.getMuxStats();
      int lastRequestEventIndex = 0;
      for (int i = 1; i < manifestDelayList.length; i++) {
        httpServer.setHLSManifestDelay(manifestDelayList[i]);
        if (!muxStats.waitForNextSegmentToLoad(waitForPlaybackToStartInMS * 3)) {
          fail("HLS playback segment did not start in " + waitForPlaybackToStartInMS + " ms !!!");
        }
//        lastRequestEventIndex = checkManifestDelayAndEvents(lastRequestEventIndex);
      }
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }

  private int checkManifestDelayAndEvents(int startEventSearchAt) throws JSONException {
    // TODO check the manifest delay
    Log.e("MuxStats", "Checking Media Segments !!!");
    int requestCompletedEventIndex = networkRequest.getIndexForNextEvent(startEventSearchAt,
        RequestCompleted.TYPE);
    if (requestCompletedEventIndex == -1) {
      fail("Missing request completed event on position: " + startEventSearchAt);
    }
    return requestCompletedEventIndex;
  }
}
