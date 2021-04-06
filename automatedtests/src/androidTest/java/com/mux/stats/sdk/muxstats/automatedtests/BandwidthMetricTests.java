package com.mux.stats.sdk.muxstats.automatedtests;

import static org.junit.Assert.fail;

import android.util.Log;
import com.mux.stats.sdk.core.events.playback.RequestCompleted;
import com.mux.stats.sdk.core.model.BandwidthMetricData;
import com.mux.stats.sdk.muxstats.MuxStats;
import com.mux.stats.sdk.muxstats.MuxStatsExoPlayer;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Request;

public class BandwidthMetricTests extends TestBase {

//  static long[] manifestDelayList = {100, 50, 150, 50, 350, 200, 250, 100, 40, 30, 25, 160, 79,
//      120, 160, 180, 190, 320, 480, 760, 920};

  static long[] manifestDelayList = {100, 50, 150, 50, 350, 200, 250, 100};

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
      }
      // TODO check request completed events
      int requestCompletedEventIndex = 1;
      while ((requestCompletedEventIndex = networkRequest.getIndexForNextEvent(
          requestCompletedEventIndex - 1, RequestCompleted.TYPE)) != -1) {
        JSONObject requestCompletedJson = networkRequest.getEventForIndex(requestCompletedEventIndex);
        // TODO check the request completed data
        long requestResponseStart = getLongValue(
            "REQUEST_START", BandwidthMetricData.REQUEST_START,
            requestCompletedEventIndex, requestCompletedJson);
        long requestResponseEnd = getLongValue(
            "REQUEST_RESPONSE_START", BandwidthMetricData.REQUEST_RESPONSE_START,
            requestCompletedEventIndex, requestCompletedJson);
        long requestBytesLoaded = getLongValue(
            "REQUEST_BYTES_LOADED", BandwidthMetricData.REQUEST_BYTES_LOADED,
            requestCompletedEventIndex, requestCompletedJson);
//        String requestType = requestCompleted.getString(BandwidthMetricData.REQUEST_TYPE);
        long requestMediaDuration = getLongValue(
            "REQUEST_MEDIA_DURATION", BandwidthMetricData.REQUEST_MEDIA_DURATION,
            requestCompletedEventIndex, requestCompletedJson);
        long requestMediaStartTime = getLongValue(
            "REQUEST_MEDIA_START_TIME", BandwidthMetricData.REQUEST_MEDIA_START_TIME,
            requestCompletedEventIndex, requestCompletedJson);
        // TODO check request_response_headers x-cdn and content-type
//        String requestHostName = requestCompleted.getString(BandwidthMetricData.REQUEST_HOSTNAME);
        // See how to implement this, and what it is
//        long requestCurrentLevel = getLongValue(
//            "REQUEST_CURRENT_LEVEL", BandwidthMetricData.REQUEST_CURRENT_LEVEL,
//            requestCompletedEventIndex, requestCompletedJson);
        long requestVideoWidth = getLongValue(
            "REQUEST_VIDEO_WIDTH", BandwidthMetricData.REQUEST_VIDEO_WIDTH,
            requestCompletedEventIndex, requestCompletedJson);
        long requestVideoHeight = getLongValue(
            "REQUEST_VIDEO_HEIGHT", BandwidthMetricData.REQUEST_VIDEO_HEIGHT,
            requestCompletedEventIndex, requestCompletedJson);
        long requestLabelBitrate = getLongValue(
            "REQUEST_LABELED_BITRATE", BandwidthMetricData.REQUEST_LABELED_BITRATE,
            requestCompletedEventIndex, requestCompletedJson);
        // TODO check request rendition list
      }
      // TODO implement request canceled event
      // TODO implement request failed event
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }

  private long getLongValue(String fieldName, String jsonKey, int eventIndex, JSONObject jo) {
    try {
      return jo.getLong(jsonKey);
    } catch (JSONException e) {
      fail("Missing " + fieldName + " for request object with index: " + eventIndex);
    }
    return -1;
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
