package com.mux.stats.sdk.muxstats.automatedtests;

import static org.junit.Assert.fail;

import com.mux.stats.sdk.core.events.playback.RequestCanceled;
import com.mux.stats.sdk.core.events.playback.RequestCompleted;
import com.mux.stats.sdk.core.events.playback.RequestFailed;
import com.mux.stats.sdk.core.model.BandwidthMetricData;
import com.mux.stats.sdk.core.model.VideoData;
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

public class BandwidthMetricTests extends AdaptiveBitStreamTestBase {

  static long[] manifestDelayList = {100, 50, 150, 50, 350, 200, 250, 100, 40, 30, 25, 160, 79,
      120, 160, 180, 190, 320, 480, 760, 920};
  // All media segments are simmilar to this duration in ms, determined by using
  // ffprobe on few media segments in asset dir, if you change hls video asset, update this value.
  static long averageSegmentDuration = 4006; // milliseconds
  // Our HLS test file have 3 levels of quality
  // Expected video width for each HLS quality level. values obtained by inspecting static assets
  static long[] hlsVideoWidthsByLevel = {640, 842, 1280};
  static long[] dashVideoWidthsByLevel = {568, 854, 1920};
  // Expected video height for each HLS quality level. values obtained by inspecting static assets
  static long[] hlsVideoHeightsByLevel = {360, 474, 720};
  static long[] dashVideoHeightsByLevel = {320, 480, 1080};
  // Declared video bitrates according to quality level
  static long[] hlsVideoBitratesByLevel = {800000, 1400000, 2800000};
  static long[] dashVideoBitratesByLevel = {234738, 1007646, 3951723};
  static long dashAudioBitrate = 127283;

  static final String X_CDN_HEADER_VALUE = "automated.test.com";

  static final long ERROR_MARGIN_FOR_REQUEST_LATENCY = 500;
  static final long ERROR_MARGIN_FOR_BYTES_SERVED = 50;
  static final long ERROR_MARGIN_FOR_SEGMENT_DURATION = 100;
  static final long ERROR_MARGIN_FOR_SEGMENT_VIDEO_RESOLUTION = 10;
  static final long ERROR_MARGIN_FOR_QUALITY_LEVEL = 0;
  static final long ERROR_MARGIN_FOR_STREAM_LABEL_BITRATE = 1000;
  static final long ERROR_MARGIN_FOR_NETWORK_REQUEST_DELAY = 30;

  private boolean parsingDash = false;

//  static long[] manifestDelayList = {100, 50, 150, 50, 350, 200, 250, 100};

  @Before
  public void init() {
    if( currentTestName.getMethodName().equalsIgnoreCase("testBandwidthMetricsHls") ) {
      urlToPlay = "http://localhost:5000/hls/google_glass/playlist.m3u8";
    } else {
      urlToPlay = "http://localhost:5000/dash/google_glass/playlist.mpd";
      parsingDash = true;
    }
    bandwidthLimitInBitsPerSecond = 12000000;
    super.init();
    httpServer.setHLSManifestDelay(manifestDelayList[0]);
    httpServer.setAdditionalHeader(SimpleHTTPServer.X_CDN_RESPONSE_HEADER, X_CDN_HEADER_VALUE);
    // Allow parsing of these headers, we need it for test
    testActivity.allowHeaderToBeSentToBackend(SimpleHTTPServer.FILE_NAME_RESPONSE_HEADER);
    testActivity.allowHeaderToBeSentToBackend(SimpleHTTPServer.REQUEST_NETWORK_DELAY_HEADER);
    testActivity.allowHeaderToBeSentToBackend(SimpleHTTPServer.REQUEST_UUID_HEADER);
  }

  @Test
  public void testBandwidthMetricsHls() {
    testBandwidthMetrics();
  }

  @Test
  public void testBandwidthMetricsDash() {
    testBandwidthMetrics();
  }

  public void testBandwidthMetrics() {
    try {
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }
      MuxStatsExoPlayer muxStats = testActivity.getMuxStats();
      for (int i = 0; i < manifestDelayList.length; i++) {
        System.out.println("Waiting for segment number: " + i);
        httpServer.setHLSManifestDelay(manifestDelayList[i]);
        if (!httpServer.waitForNextSegmentToLoad(waitForPlaybackToStartInMS * 3)) {
          fail("HLS playback segment did not start in " + waitForPlaybackToStartInMS + " ms !!!");
        }
      }
      testActivity.runOnUiThread(new Runnable() {
        public void run() {
          pView.getPlayer().stop();
        }
      });
      checkMimeType();
      ArrayList<JSONObject> requestCompletedEvents = networkRequest
          .getAllEventsOfType(RequestCompleted.TYPE);
      ArrayList<JSONObject> requestCanceledEvents = networkRequest
          .getAllEventsOfType(RequestCanceled.TYPE);
      ArrayList<JSONObject> requestFailedEvents = networkRequest
          .getAllEventsOfType(RequestFailed.TYPE);
      checkRequests(requestCompletedEvents,
          true, false, false);
      checkRequests(requestCanceledEvents,
          false, true, false);
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }

  private void checkRequests(ArrayList<JSONObject> requests,
      boolean isRequestCompletedList, boolean isRequestCanceledList,
      boolean isRequestFailedList) throws Exception {
    for (JSONObject requestJson : requests) {
      int qualityLevel = getSelectedRenditionIndex();
      if (!requestJson.has(BandwidthMetricData.REQUEST_RESPONSE_HEADERS)) {
        fail("Missing response headers for event: \n" + requestJson.toString());
      }
      String headerString = requestJson
          .getString(BandwidthMetricData.REQUEST_RESPONSE_HEADERS);
      JSONObject headersJsonObject = new JSONObject(headerString);
      String fileNameHeaderValue = headersJsonObject
          .getString(SimpleHTTPServer.FILE_NAME_RESPONSE_HEADER);
      String segmentUuid = headersJsonObject.getString(SimpleHTTPServer.REQUEST_UUID_HEADER);
      long networkDelay = headersJsonObject.getLong(SimpleHTTPServer.REQUEST_NETWORK_DELAY_HEADER);
      boolean expectingManifest = false;
      int mediaSegmentIndex = 0;
      if (fileNameHeaderValue.endsWith("m3u8") || fileNameHeaderValue.endsWith("mpd")
        || (fileNameHeaderValue.contains("segment_") && fileNameHeaderValue.endsWith("mp4"))) {
        expectingManifest = true;
      } else {
        String[] segments = fileNameHeaderValue.split("_");
        String indexSegment = segments[segments.length - 1];
        if (parsingDash) {
          mediaSegmentIndex = parseMediaIndexForDash(indexSegment);
        } else {
          // Parsing HLS
          mediaSegmentIndex = Integer.valueOf(indexSegment.replaceAll("[^0-9]", ""));
        }
      }
      SegmentStatistics segmentStat = httpServer.getSegmentStatistics(segmentUuid);
      if (segmentStat == null) {
        fail("Failed to obtain segment statistics for file: " + fileNameHeaderValue
            + ", this is automated test related error !!!");
      }
      int requestCompletedEventIndex = requestJson.getInt(MockNetworkRequest.EVENT_INDEX);
      // check the request completed data
      if (requestJson.has(BandwidthMetricData.REQUEST_TYPE)) {
        if (isRequestCompletedList) {
          checkRequestCompletedEvent(requestJson,
              segmentStat, mediaSegmentIndex, qualityLevel,
              requestCompletedEventIndex, fileNameHeaderValue, networkDelay);
        }
        if (isRequestCanceledList) {
          checkRequestCanceledEvent(requestJson,
              segmentStat, requestCompletedEventIndex, fileNameHeaderValue);
        }
//        if (isRequestFailedList) {
//          checkRequestFailedEvent(requestJson,
//              segmentStat, requestCompletedEventIndex, fileNameHeaderValue);
//        }
      } else {
        fail("Request complete event missing requestType, this field is "
            + "mandatory, event index: " + requestCompletedEventIndex);
      }
    }
  }

  private void checkRequestFailedEvent(JSONObject requestJson,
      SegmentStatistics segmentStat,
      int requestCompletedEventIndex,
      String segmentUrl)
      throws Exception {
    checkManifestValue(requestJson, segmentUrl);
    checkLongValueForEvent(
        "REQUEST_RESPONSE_START", BandwidthMetricData.REQUEST_RESPONSE_START,
        requestCompletedEventIndex, requestJson, segmentStat.getSegmentRequestedAt(),
        ERROR_MARGIN_FOR_REQUEST_LATENCY);
    checkLongValueForEvent(
        "REQUEST_RESPONSE_END", BandwidthMetricData.REQUEST_RESPONSE_END,
        requestCompletedEventIndex, requestJson, segmentStat.getSegmentRespondedAt(),
        ERROR_MARGIN_FOR_REQUEST_LATENCY);
    checkStringValueForEvent("REQUEST_URL", BandwidthMetricData.REQUEST_URL,
        requestCompletedEventIndex, requestJson, segmentUrl);
    checkStringValueForEvent("REQUEST_HOSTNAME", BandwidthMetricData.REQUEST_HOSTNAME,
        requestCompletedEventIndex, requestJson, "localhost");
    if (!requestJson.has(BandwidthMetricData.REQUEST_ERROR)) {
      fail("Request failed missing REQUEST_ERROR field for event index: "
          + requestCompletedEventIndex);
    }
    if (!requestJson.has(BandwidthMetricData.REQUEST_ERROR_CODE)) {
      fail("Request failed missing REQUEST_ERROR_CODE field for event index: "
          + requestCompletedEventIndex);
    }
    if (!requestJson.has(BandwidthMetricData.REQUEST_ERROR_TEXT)) {
      fail("Request failed missing REQUEST_ERROR_TEXT field for event index: "
          + requestCompletedEventIndex);
    }
  }

  private void checkRequestCanceledEvent(JSONObject requestJson,
      SegmentStatistics segmentStat,
      int requestCompletedEventIndex,
      String segmentUrl)
      throws Exception {
    checkManifestValue(requestJson, segmentUrl);
    checkLongValueForEvent(
        "REQUEST_RESPONSE_START", BandwidthMetricData.REQUEST_RESPONSE_START,
        requestCompletedEventIndex, requestJson, segmentStat.getSegmentRequestedAt(),
        ERROR_MARGIN_FOR_REQUEST_LATENCY);
    checkLongValueForEvent(
        "REQUEST_RESPONSE_END", BandwidthMetricData.REQUEST_RESPONSE_END,
        requestCompletedEventIndex, requestJson, segmentStat.getSegmentRespondedAt(),
        ERROR_MARGIN_FOR_REQUEST_LATENCY);
    if (!requestJson.has(BandwidthMetricData.REQUEST_CANCEL)) {
      fail("Request canceled missing REQUEST_CANCEL field for event index: "
          + requestCompletedEventIndex);
    }
    checkStringValueForEvent("REQUEST_URL", BandwidthMetricData.REQUEST_URL,
        requestCompletedEventIndex, requestJson, segmentUrl);
    checkStringValueForEvent("REQUEST_HOSTNAME", BandwidthMetricData.REQUEST_HOSTNAME,
        requestCompletedEventIndex, requestJson, "localhost");
  }

  private void checkRequestCompletedEvent(JSONObject requestJson,
      SegmentStatistics segmentStat,
      int mediaSegmentIndex,
      int qualityLevel,
      int requestCompletedEventIndex,
      String fileNameHeaderValue,
      long requestNetworkDelay
  ) throws Exception {
    if (checkManifestValue(requestJson, fileNameHeaderValue)) {
      checkManifestSegment(requestCompletedEventIndex, requestJson, segmentStat);
    } else {
      checkNetworkDelay(requestJson, requestNetworkDelay, requestCompletedEventIndex);
      if (fileNameHeaderValue.contains("English")) {
        checkRequestCompletedAudioSegment(requestCompletedEventIndex, requestJson,
            mediaSegmentIndex, qualityLevel, fileNameHeaderValue, segmentStat);
      } else {
        checkRequestCompletedMediaSegment(requestCompletedEventIndex, requestJson,
            mediaSegmentIndex, qualityLevel, fileNameHeaderValue, segmentStat);
      }
    }
  }

  private void checkNetworkDelay(JSONObject requestJson, long expectedNetworkDelay,
      int eventIndex) {
    // I have no way of getting REQUEST_START
  }

  private boolean checkManifestValue(JSONObject requestJson, String segmentUrl) throws Exception {
    String reportedManifest = requestJson.getString(BandwidthMetricData.REQUEST_TYPE);
    String expectedResult = "media";
    boolean isManifest = false;
    if (segmentUrl.endsWith("m3u8") || segmentUrl.endsWith("mpd")) {
      expectedResult = "manifest";
      isManifest = true;
    }
    else if (parsingDash && segmentUrl.endsWith("mp4")) {
      if (segmentUrl.contains("English")) {
        expectedResult = "audio_init";
      } else {
        expectedResult = "video_init";
      }
      isManifest = true;
    }
    if (!reportedManifest
        .equalsIgnoreCase(expectedResult)) {
        fail("Expected requestcompleted event type to be: " + expectedResult
            + ", reported: " + reportedManifest
            + " event: " + requestJson.toString());
    }
    return isManifest;
  }

  private void checkManifestSegment(int requestCompletedEventIndex, JSONObject requestCompletedJson,
      SegmentStatistics segmentStat) {
    checkLongValueForEvent(
        "REQUEST_RESPONSE_START", BandwidthMetricData.REQUEST_RESPONSE_START,
        requestCompletedEventIndex, requestCompletedJson, segmentStat.getSegmentRequestedAt(),
        ERROR_MARGIN_FOR_REQUEST_LATENCY);
    checkLongValueForEvent(
        "REQUEST_RESPONSE_END", BandwidthMetricData.REQUEST_RESPONSE_END,
        requestCompletedEventIndex, requestCompletedJson, segmentStat.getSegmentRespondedAt(),
        ERROR_MARGIN_FOR_REQUEST_LATENCY);
    checkLongValueForEvent(
        "REQUEST_BYTES_LOADED", BandwidthMetricData.REQUEST_BYTES_LOADED,
        requestCompletedEventIndex, requestCompletedJson, segmentStat.getSegmentLengthInBytes(),
        ERROR_MARGIN_FOR_BYTES_SERVED);
  }

  private void checkRequestCompletedAudioSegment(int requestCompletedEventIndex,
      JSONObject requestCompletedJson,
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
    checkLongValueForEvent(
        "REQUEST_LABELED_BITRATE", BandwidthMetricData.REQUEST_LABELED_BITRATE,
        requestCompletedEventIndex, requestCompletedJson, dashAudioBitrate,
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
    if (parsingDash) {
      checkHeaders(requestCompletedEventIndex, requestCompletedJson, "video/mp4");
    } else {
      checkHeaders(requestCompletedEventIndex, requestCompletedJson, "video/mp2t");
    }
    checkRenditionList(requestCompletedEventIndex, requestCompletedJson);
  }

  private void checkRequestCompletedMediaSegment(int requestCompletedEventIndex,
      JSONObject requestCompletedJson,
      int mediaSegmentIndex, int qualityLevel, String segmentUrl, SegmentStatistics segmentStats)
      throws Exception {
    long[] videoWidthsByLevel = hlsVideoWidthsByLevel;
    long[] videoHeightsByLevel = hlsVideoHeightsByLevel;
    long[] videoBitratesByLevel = hlsVideoBitratesByLevel;
    if (parsingDash) {
      videoWidthsByLevel = dashVideoWidthsByLevel;
      videoHeightsByLevel = dashVideoHeightsByLevel;
      videoBitratesByLevel = dashVideoBitratesByLevel;
    }

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
    if (parsingDash) {
      checkHeaders(requestCompletedEventIndex, requestCompletedJson, "video/mp4");
    } else {
      checkHeaders(requestCompletedEventIndex, requestCompletedJson, "video/mp2t");
    }
    checkRenditionList(requestCompletedEventIndex, requestCompletedJson);
  }

  private void checkRenditionList(int requestCompletedEventIndex,
      JSONObject requestCompletedJson) throws Exception {
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
        fail("Value for field: " + fieldName + " do not match, expected: " + expectedValue
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
        fail("Value for field: " + fieldName + " do not match expected value, expected: "
            + expectedValue + ",actual value: " + value + ",diff: " + (value - expectedValue)
            + ",error margin: " + errorMargin + ",for eventIndex: " + eventIndex
            + ",event:\n" + jo.toString());
      }
    } catch (JSONException e) {
      fail("Missing " + fieldName + " for request object with index: " + eventIndex);
    }
  }

  private int parseMediaIndexForDash(String segmentString) {
    String segmentWithouthExt = segmentString.replace(".m4s", "");
    // Substract 1 because dash segments start from 1, and HLS start from 0
    return (Integer.valueOf(segmentWithouthExt.replaceAll("[^0-9]", "")) - 1);
  }

  private void checkMimeType() throws JSONException {
    String mimeType = "unknown";
    String expectedMimeType = "application/x-mpegurl";
    for (int i = 0; i < networkRequest.getNumberOfReceivedEvents(); i++) {
      JSONObject event = networkRequest.getEventForIndex(i);
      if (event.has(VideoData.VIDEO_SOURCE_MIME_TYPE)) {
        mimeType = event.getString(VideoData.VIDEO_SOURCE_MIME_TYPE);
      }
    }
    if (parsingDash) {
      expectedMimeType = "application/dash+xml";
    }
    if (!mimeType.equalsIgnoreCase("unknown")) {
      fail("Unexpected mime type, reported: " + mimeType + ", expected: "
          + expectedMimeType);
    }
  }
}
