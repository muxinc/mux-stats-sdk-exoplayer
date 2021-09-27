package com.mux.stats.sdk.muxstats.automatedtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.mux.stats.sdk.core.events.playback.EndedEvent;
import com.mux.stats.sdk.core.events.playback.PauseEvent;
import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.RebufferEndEvent;
import com.mux.stats.sdk.core.events.playback.RebufferStartEvent;
import com.mux.stats.sdk.core.events.playback.ViewEndEvent;
import com.mux.stats.sdk.core.events.playback.ViewStartEvent;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.core.model.PlayerData;
import com.mux.stats.sdk.muxstats.MuxStatsExoPlayer;
import com.mux.stats.sdk.muxstats.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class PlaybackTests extends TestBase {

  public static final String TAG = "playbackTest";
  static final String secondVideoToPlayUrl = "http://localhost:5000/hls/google_glass/playlist.m3u8";

  @Test
  public void testVideoChange() {
    testVideoChange(false, true);
  }

  @Test
  public void testProgramChange() {
    testVideoChange(true, false);
  }

  @Test
  public void testVideoVodPlayback() {
    testVodPlayback(false,10000, 72000, 500);
  }

  public void testVideoChange(boolean programChange, boolean videoChange) {
    try {
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }
      Thread.sleep(PAUSE_PERIOD_IN_MS);
      // Video started, do video change, we expect to see fake rebufferstart
      testActivity.runOnUiThread(new Runnable() {
        public void run() {
          testActivity.setUrlToPlay(secondVideoToPlayUrl);
          CustomerVideoData customerVideoData = new CustomerVideoData();
          customerVideoData.setVideoTitle(BuildConfig.FLAVOR + "-" + currentTestName.getMethodName()
              + "_title_2");
          MuxStatsExoPlayer muxStats = testActivity.getMuxStats();
          if (videoChange) {
            muxStats.videoChange(customerVideoData);
          }
          if (programChange) {
            muxStats.programChange(customerVideoData);
          }
          testActivity.startPlayback();
        }
      });
      Thread.sleep(PAUSE_PERIOD_IN_MS);
      int rebufferStartEventIndex = 0;
      int rebufferEndEventIndex;
      while ((rebufferStartEventIndex = networkRequest.getIndexForNextEvent(
          rebufferStartEventIndex, RebufferStartEvent.TYPE)) != -1) {
        rebufferEndEventIndex = networkRequest.getIndexForNextEvent(rebufferStartEventIndex,
            RebufferEndEvent.TYPE);
        if (rebufferEndEventIndex == -1) {
          fail("We have rebuffer start event at position: " + rebufferStartEventIndex
              + ",without matching rebuffer end event, events: "
              + networkRequest.getReceivedEventNames());
        }
      }
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }

  @Test
  public void testEndEvents() {
    try {
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }
      // Seek backward, stage 4
      testActivity.runOnUiThread(new Runnable() {
        public void run() {
          long contentDuration = pView.getPlayer().getContentDuration();
          pView.getPlayer().seekTo(contentDuration - 2000);
        }
      });
      if (!testActivity.waitForPlaybackToFinish(waitForPlaybackToStartInMS)) {
        fail("Playback did not finish in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }
//      testActivity.finishAffinity();
      testActivity.finishActivity(0);
      Thread.sleep(PAUSE_PERIOD_IN_MS);
      int pauseIndex = networkRequest.getIndexForFirstEvent(PauseEvent.TYPE);
      int endedIndex = networkRequest.getIndexForFirstEvent(EndedEvent.TYPE);
      int viewEndEventIndex = networkRequest.getIndexForFirstEvent(ViewEndEvent.TYPE);
      if (viewEndEventIndex == -1 || endedIndex == -1 || pauseIndex == -1) {
        fail("Missing end events: viewEndEventIndex = " + viewEndEventIndex
            + ", endedEventIndex: " + endedIndex
            + ", pauseEventIndex: " + pauseIndex);
      }
      if (!(pauseIndex < endedIndex && endedIndex < viewEndEventIndex)) {
        fail("End events not ordered correctly: viewEndEventIndex = " + viewEndEventIndex
            + ", endedEventIndex: " + endedIndex
            + ", pauseEventIndex: " + pauseIndex);
      }
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }

  @Test
  public void testRebufferingAndStartupTime() {
    try {
      testActivity.waitForActivityToInitialize();
      long testStartedAt = System.currentTimeMillis();
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }
      long expectedStartupTime = System.currentTimeMillis() - testStartedAt;

      // play x seconds
      Thread.sleep(PLAY_PERIOD_IN_MS);
      jamNetwork();
      testActivity.waitForPlaybackToStartBuffering();
      long rebufferStartedAT = System.currentTimeMillis();

      // Wait for rebuffer to complete
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }

      long measuredRebufferPeriod = System.currentTimeMillis() - rebufferStartedAT;
      // play x seconds
      Thread.sleep(PLAY_PERIOD_IN_MS * 2);
//            exitActivity();
//            testScenario.close();

      // Startup time check
      int viewstartIndex = networkRequest.getIndexForFirstEvent(ViewStartEvent.TYPE);
      int playIndex = networkRequest.getIndexForFirstEvent(PlayEvent.TYPE);
      int playingIndex = networkRequest.getIndexForFirstEvent(PlayingEvent.TYPE);
      // Check if viewstart and playing events are received
      if (viewstartIndex == -1) {
        fail("viewstart event not received !!!");
      }
      if (playIndex == -1) {
        fail("play event not received !!!");
      }
      if (playingIndex == -1) {
        fail("playing event not received !!!");
      }

      long reportedStartupTime = networkRequest.getCreationTimeForEvent(playingIndex) -
          networkRequest.getCreationTimeForEvent(viewstartIndex);
      // Check if startup time match with in 200 ms precission
      if (Math.abs(reportedStartupTime - expectedStartupTime) > 500) {
        fail("Reported startup time and expected startup time do not match within 500 ms,"
            + "reported time: " + reportedStartupTime + ", measured startup time: "
            + expectedStartupTime);
      }

      // check rebuffering events
      int rebufferStartEventIndex = networkRequest.getIndexForFirstEvent(RebufferStartEvent.TYPE);
      int rebufferEndEventIndex = networkRequest.getIndexForFirstEvent(RebufferEndEvent.TYPE);
      // Check if rebuffer events are received
      if (rebufferStartEventIndex == -1) {
        fail("rebufferstart event not received !!!");
      }
      if (rebufferEndEventIndex == -1) {
        fail("rebufferend event not received !!!");
      }
      if (rebufferStartEventIndex > rebufferEndEventIndex) {
        fail("rebufferend received before rebufferstart event !!!");
      }
      int secondPlayIndex = networkRequest.getIndexForLastEvent(PlayEvent.TYPE);
      int secondPlayingIndex = networkRequest.getIndexForLastEvent(PlayingEvent.TYPE);
      if (secondPlayIndex != playIndex) {
        fail("Play event received after rebufferend this is not good  ! event: "
            + networkRequest.getReceivedEventNames());
      }
      if (secondPlayingIndex == playingIndex) {
        fail("Playing event not received after ebufferEnd ! events: "
            + networkRequest.getReceivedEventNames());
      }
      // TODO see what is the best way to calculate rebuffer period
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }
}