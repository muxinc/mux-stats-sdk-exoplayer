package com.mux.stats.sdk.muxstats.automatedtests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import android.util.Log;
import com.mux.stats.sdk.core.events.playback.RequestCompleted;
import com.mux.stats.sdk.core.events.playback.RequestFailed;
import com.mux.stats.sdk.core.model.BandwidthMetricData;
import com.mux.stats.sdk.muxstats.MuxStats;
import com.mux.stats.sdk.muxstats.MuxStatsExoPlayer;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.MockNetworkRequest;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.http.SegmentStatistics;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.http.SimpleHTTPServer;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Request;

public class BandwidthMetricTests extends AdaptiveBitStreamTestBase {

  static long[] manifestDelayList = {100, 50, 150, 50, 350, 200, 250, 100, 40, 30, 25, 160, 79,
      120, 160, 180, 190, 320, 480, 760, 920};
  // All media segments are simmilar to this duration in ms, determined by using
  // ffprobe on few media segments in asset dir, if you change hls video asset, update this value.
  static long averageSegmentDuration = 4006; // milliseconds
  // Our HLS test file have 3 levels of quality
  // Expected video width for each HLS quality level. values obtained by inspecting static assets
  static long[] videoWidthsByLevel = {640, 842, 1280};
  // Expected video height for each HLS quality level. values obtained by inspecting static assets
  static long[] videoHeightsByLevel = {360, 474, 720};
  // Declared video bitrates according to quality level
  static long[] videoBitratesByLevel = {800000, 1400000, 2800000};

  static final String X_CDN_HEADER_VALUE = "automated.test.com";

  static final long ERROR_MARGIN_FOR_REQUEST_LATENCY = 500;
  static final long ERROR_MARGIN_FOR_BYTES_SERVED = 50;
  static final long ERROR_MARGIN_FOR_SEGMENT_DURATION = 100;
  static final long ERROR_MARGIN_FOR_SEGMENT_VIDEO_RESOLUTION = 10;
  static final long ERROR_MARGIN_FOR_QUALITY_LEVEL = 0;
  static final long ERROR_MARGIN_FOR_STREAM_LABEL_BITRATE = 1000;

//  static long[] manifestDelayList = {100, 50, 150, 50, 350, 200, 250, 100};

  @Before
  public void init() {
    urlToPlay = "http://localhost:5000/hls/google_glass/playlist.m3u8";
    bandwidthLimitInBitsPerSecond = 12000000;
    super.init();
    httpServer.setHLSManifestDelay(manifestDelayList[0]);
    httpServer.setAdditionalHeader(SimpleHTTPServer.X_CDN_RESPONSE_HEADER, X_CDN_HEADER_VALUE);
  }

  @Test
  public void testBandwidthMetrics() {
    try {
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }
      MuxStatsExoPlayer muxStats = testActivity.getMuxStats();
      for (int i = 0; i < manifestDelayList.length; i++) {
        System.out.println("Waiting for segment number: " + i);
        httpServer.setHLSManifestDelay(manifestDelayList[i]);
        if (!muxStats.waitForNextSegmentToLoad(waitForPlaybackToStartInMS * 3)) {
          fail("HLS playback segment did not start in " + waitForPlaybackToStartInMS + " ms !!!");
        }
      }
      testActivity.runOnUiThread(new Runnable() {
        public void run() {
          pView.getPlayer().stop();
        }
      });

      int qualityLevel = getSelectedRenditionIndex();
      int requestIndex = 0;
      ArrayList<JSONObject> requestCompletedEvents = networkRequest
          .getAllEventsOfType(RequestCompleted.TYPE);
      ArrayList<JSONObject> requestFailedEvents = networkRequest
          .getAllEventsOfType(RequestFailed.TYPE);

      for (JSONObject requestCompletedJson: requestCompletedEvents) {
        String headerString = requestCompletedJson
            .getString(BandwidthMetricData.REQUEST_RESPONSE_HEADERS);
        JSONObject headersJsonObject = new JSONObject(headerString);
        String fileNameHeader = headersJsonObject
            .getString(SimpleHTTPServer.FILE_NAME_RESPONSE_HEADER);
        String segmentUuid = headersJsonObject.getString(SimpleHTTPServer.REQUEST_UUID_HEADER);
        boolean expectingManifest = false;
        int mediaSegmentIndex = 0;
        if (fileNameHeader.endsWith("m3u8")) {
          expectingManifest = true;
        } else {
          String[] segments = fileNameHeader.split("_");
          String indexSegment = segments[segments.length - 1];
          mediaSegmentIndex = Integer.valueOf(indexSegment.replaceAll("[^0-9]", ""));
        }
        SegmentStatistics segmentStat = httpServer.getSegmentStatistics(segmentUuid);
        if (segmentStat == null) {
          fail("Failed to obtain segment statistics for index: " + requestIndex
              + ", this is automated test related error !!!");
        }
        int requestCompletedEventIndex = requestCompletedJson.getInt(MockNetworkRequest.EVENT_INDEX);
        // check the request completed data
        if (requestCompletedJson.has(BandwidthMetricData.REQUEST_TYPE)) {
          if (requestCompletedJson.getString(BandwidthMetricData.REQUEST_TYPE)
              .equalsIgnoreCase("manifest")) {
            if (!expectingManifest) {
              fail("Expected requestcompleted event type to be manifest, event: "
                  + requestCompletedJson.toString());
            }
            checkManifestSegment(requestCompletedEventIndex, requestCompletedJson, segmentStat);
          }
          if (requestCompletedJson.getString(BandwidthMetricData.REQUEST_TYPE)
              .equalsIgnoreCase("media")) {
            if (expectingManifest) {
              fail("Expected requestcompleted event type to be media, event: "
                  + requestCompletedJson.toString());
            }
            checkMediaSegment(requestCompletedEventIndex, requestCompletedJson,
                mediaSegmentIndex, qualityLevel, fileNameHeader, segmentStat);
          }
          // TODO support othere event types
        } else {
          fail("Request complete evennt missing requestType, this field is "
              + "mandatory, event index: " + requestCompletedEventIndex);
        }
        // TODO check request rendition list
        requestIndex++;
      }
      // TODO implement request canceled event
      // TODO implement request failed event
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }

  private void checkManifestSegment(int requestCompletedEventIndex, JSONObject requestCompletedJson,
      SegmentStatistics segmentStat) {
    checkLongValueForEvent(
        "REQUEST_START", BandwidthMetricData.REQUEST_START,
        requestCompletedEventIndex, requestCompletedJson, segmentStat.getSegmentRequestedAt(),
        ERROR_MARGIN_FOR_REQUEST_LATENCY);
    checkLongValueForEvent(
        "REQUEST_RESPONSE_START", BandwidthMetricData.REQUEST_RESPONSE_START,
        requestCompletedEventIndex, requestCompletedJson, segmentStat.getSegmentRespondedAt(),
        ERROR_MARGIN_FOR_REQUEST_LATENCY);
    checkLongValueForEvent(
        "REQUEST_BYTES_LOADED", BandwidthMetricData.REQUEST_BYTES_LOADED,
        requestCompletedEventIndex, requestCompletedJson, segmentStat.getSegmentLengthInBytes(),
        ERROR_MARGIN_FOR_BYTES_SERVED);
  }

  private void checkMediaSegment(int requestCompletedEventIndex, JSONObject requestCompletedJson,
      int mediaSegmentIndex, int qualityLevel, String segmentUrl, SegmentStatistics segmentStats)
      throws Exception {
    checkLongValueForEvent(
        "REQUEST_MEDIA_DURATION", BandwidthMetricData.REQUEST_MEDIA_DURATION,
        requestCompletedEventIndex, requestCompletedJson, averageSegmentDuration,
        ERROR_MARGIN_FOR_SEGMENT_DURATION);
    checkLongValueForEvent(
        "REQUEST_MEDIA_START_TIME", BandwidthMetricData.REQUEST_MEDIA_START_TIME,
        requestCompletedEventIndex, requestCompletedJson,
        mediaSegmentIndex * averageSegmentDuration, ERROR_MARGIN_FOR_REQUEST_LATENCY);
    checkLongValueForEvent(
        "REQUEST_CURRENT_LEVEL", BandwidthMetricData.REQUEST_CURRENT_LEVEL,
        requestCompletedEventIndex, requestCompletedJson, qualityLevel,
        ERROR_MARGIN_FOR_SEGMENT_VIDEO_RESOLUTION);
    checkLongValueForEventIfFieldIsPresent(
        "REQUEST_VIDEO_WIDTH", BandwidthMetricData.REQUEST_VIDEO_WIDTH,
        requestCompletedEventIndex, requestCompletedJson, videoWidthsByLevel[qualityLevel],
        ERROR_MARGIN_FOR_SEGMENT_VIDEO_RESOLUTION);
    checkLongValueForEventIfFieldIsPresent(
        "REQUEST_VIDEO_HEIGHT", BandwidthMetricData.REQUEST_VIDEO_HEIGHT,
        requestCompletedEventIndex, requestCompletedJson, videoHeightsByLevel[qualityLevel],
        ERROR_MARGIN_FOR_SEGMENT_VIDEO_RESOLUTION);
    checkLongValueForEvent(
        "REQUEST_LABELED_BITRATE", BandwidthMetricData.REQUEST_LABELED_BITRATE,
        requestCompletedEventIndex, requestCompletedJson, videoBitratesByLevel[qualityLevel],
        ERROR_MARGIN_FOR_STREAM_LABEL_BITRATE);
    checkLongValueForEvent(
        "REQUEST_RESPONSE_START", BandwidthMetricData.REQUEST_RESPONSE_START,
        requestCompletedEventIndex, requestCompletedJson,
        segmentStats.getSegmentRequestedAt(),
        ERROR_MARGIN_FOR_REQUEST_LATENCY);
    checkLongValueForEvent(
        "REQUEST_RESPONSE_END", BandwidthMetricData.REQUEST_RESPONSE_END,
        requestCompletedEventIndex, requestCompletedJson,
        segmentStats.getSegmentRespondedAt(),
        ERROR_MARGIN_FOR_REQUEST_LATENCY);
    checkStringValueForEvent("REQUEST_URL", BandwidthMetricData.REQUEST_URL,
        requestCompletedEventIndex, requestCompletedJson, segmentUrl);
    checkStringValueForEvent("REQUEST_HOSTNAME", BandwidthMetricData.REQUEST_HOSTNAME,
        requestCompletedEventIndex, requestCompletedJson, "localhost");
    checkHeaders(requestCompletedEventIndex, requestCompletedJson, "video/mp2t");
    checkRenditionList(requestCompletedEventIndex, requestCompletedJson);
  }

  private void checkRenditionList(int requestCompletedEventIndex,
      JSONObject requestCompletedJson) throws Exception {
//    String renditionLString = requestCompletedJson
//        .getString(BandwidthMetricData.REQUEST_RENDITION_LISTS);
//    JSONArray renditionListJson = new JSONArray(renditionLString);
//    for (int i = 0; i < renditionListJson.length(); i ++) {
//      JSONObject renditionJson = renditionListJson.getJSONObject(i);
//      int renditionWidth = renditionJson.getInt()
//    }
  }

  private void checkHeaders(int requestCompletedEventIndex,
      JSONObject requestCompletedJson, String expectedContentType)
      throws Exception {
    String headerString = requestCompletedJson
        .getString(BandwidthMetricData.REQUEST_RESPONSE_HEADERS);
    JSONObject headersJsonObject = new JSONObject(headerString);
    String xCdnHeader = headersJsonObject.getString(SimpleHTTPServer.X_CDN_RESPONSE_HEADER);
    String contentTypeHeader = headersJsonObject
        .getString(SimpleHTTPServer.CONTENT_TYPE_RESPONSE_HEADER);
    if (!xCdnHeader.equalsIgnoreCase(X_CDN_HEADER_VALUE)) {
      failOnHeaderValue(SimpleHTTPServer.X_CDN_RESPONSE_HEADER, xCdnHeader, X_CDN_HEADER_VALUE,
          requestCompletedEventIndex, requestCompletedJson.toString());
    }
    if (!contentTypeHeader.equalsIgnoreCase(expectedContentType)) {
      failOnHeaderValue(SimpleHTTPServer.CONTENT_TYPE_RESPONSE_HEADER, contentTypeHeader,
          expectedContentType, requestCompletedEventIndex, requestCompletedJson.toString());
    }
  }

  private void failOnHeaderValue(String headerName, String headerValue, String expectedValue,
      int requestIndex, String eventJson) {
    fail("Wrong value for header: " + headerName
        + ", we got: " + headerValue + ", we expected: " + expectedValue
        + ",for eventIndex: " + requestIndex
        + ",event:\n" + eventJson);
  }

  private void checkLongValueForEventIfFieldIsPresent(String fieldName, String jsonKey,
      int eventIndex, JSONObject jo,
      long expectedValue, long errorMargin) {
    if (jo.has(jsonKey)) {
      checkLongValueForEvent(fieldName, jsonKey, eventIndex, jo, expectedValue, errorMargin);
    }
  }

  private void checkStringValueForEvent(String fieldName, String jsonKey, int eventIndex,
      JSONObject jo, String expectedValue) {
    try {
      String value = jo.getString(jsonKey);
      if (!value.contains(expectedValue)) {
        fail("Expected value for field: " + fieldName + ",expected: " + expectedValue
            + ",actual value: " + value + ",for eventIndex: " + eventIndex
            + ",event:\n" + jo.toString());
      }
    } catch (JSONException e) {
      fail("Missing " + fieldName + " for request object with index: " + eventIndex);
    }
  }

  private void checkLongValueForEvent(String fieldName, String jsonKey, int eventIndex,
      JSONObject jo,
      long expectedValue, long errorMargin) {
    try {
      long value = jo.getLong(jsonKey);
      long diff = value - expectedValue;
      if (Math.abs(diff) > errorMargin) {
        fail("Expected value for field: " + fieldName + ",expected: " + expectedValue
            + ",actual value: " + value + ",diff: " + (value - expectedValue)
            + ",error margin: " + errorMargin + ",for eventIndex: " + eventIndex
            + ",event:\n" + jo.toString());
      }
    } catch (JSONException e) {
      fail("Missing " + fieldName + " for request object with index: " + eventIndex);
    }
  }
}
