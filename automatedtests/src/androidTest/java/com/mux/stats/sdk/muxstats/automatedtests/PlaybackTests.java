package com.mux.stats.sdk.muxstats.automatedtests;

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
import com.mux.stats.sdk.muxstats.R;
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
      testActivity.finishAffinity();
      Thread.sleep(PAUSE_PERIOD_IN_MS);
      int pauseIndex = networkRequest.getIndexForFirstEvent(PauseEvent.TYPE);
      int endedIndex = networkRequest.getIndexForFirstEvent(EndedEvent.TYPE);
      int viewEndEventIndex = networkRequest.getIndexForFirstEvent(ViewEndEvent.TYPE);
      if (viewEndEventIndex == -1 || endedIndex == -1 || pauseIndex == -1) {
        fail("Missing end events: viewEndEventIndex = " + viewEndEventIndex
            + ", viewEndEventIndex: " + viewEndEventIndex
            + ", pauseEventIndex: " + pauseIndex);
      }
      if (!(pauseIndex < endedIndex && endedIndex < viewEndEventIndex)) {
        fail("End events not ordered correctly: viewEndEventIndex = " + viewEndEventIndex
            + ", viewEndEventIndex: " + viewEndEventIndex
            + ", pauseEventIndex: " + pauseIndex);
      }
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }

  /*
   * According to the self validation guid: https://docs.google.com/document/d/1FU_09N3Cg9xfh784edBJpgg3YVhzBA6-bd5XHLK7IK4/edit#
   * We are implementing vod playback scenario.
   */
  @Test
  public void testVodPlayback() {
    try {
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }

      // Init player controlls
      controlView = pView.findViewById(R.id.exo_controller);
      if (controlView != null) {
        pauseButton = controlView.findViewById(R.id.exo_pause);
        playButton = controlView.findViewById(R.id.exo_play);
      }
      initPlayerControls();

      // play x seconds, stage 1
      Thread.sleep(PLAY_PERIOD_IN_MS);
      pausePlayer();
      // Pause x seconds, stage 2
      Thread.sleep(PAUSE_PERIOD_IN_MS);
      // Resume video, stage 3
      resumePlayer();
      // Play another x seconds
      Thread.sleep(PLAY_PERIOD_IN_MS);

      // Seek backward, stage 4
      testActivity.runOnUiThread(new Runnable() {
        public void run() {
          long currentPlaybackPosition = pView.getPlayer().getCurrentPosition();
          pView.getPlayer().seekTo(currentPlaybackPosition / 2);
        }
      });

      // Play another x seconds, stage 5
      Thread.sleep(PLAY_PERIOD_IN_MS);

      // seek forward in the video, stage 6
      testActivity.runOnUiThread(new Runnable() {
        public void run() {
          long currentPlaybackPosition = pView.getPlayer()
              .getCurrentPosition();
          long videoDuration = pView.getPlayer().getDuration();
          long seekToInFuture =
              currentPlaybackPosition + ((videoDuration - currentPlaybackPosition) / 2);
          pView.getPlayer().seekTo(seekToInFuture);
        }
      });

      // Play another x seconds, stage 7
      Thread.sleep(PLAY_PERIOD_IN_MS * 2);

      CheckupResult result;

      // Check first playback period, stage 1
      result = checkPlaybackPeriodAtIndex(0, PLAY_PERIOD_IN_MS);

      // Check pause period, stage 2
      result = checkPausePeriodAtIndex(result.eventIndex, PAUSE_PERIOD_IN_MS);

      // Check playback period, stage 3
      result = checkPlaybackPeriodAtIndex(result.eventIndex - 1, PLAY_PERIOD_IN_MS);

      // Check SeekEvents, stage 4
      result = checkSeekAtIndex(result.eventIndex);

      // check playback period stage 5
      result = checkPlaybackPeriodAtIndex(result.eventIndex,
          PLAY_PERIOD_IN_MS - result.seekPeriod);

      // check seeking, stage 6
      result = checkSeekAtIndex(result.eventIndex);

      // Exit the player with back button
//            testScenario.close();
      Log.w(TAG, "See what event should be dispatched on view closed !!!");
      // TODO check player end event
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
    Log.e(TAG, "All done !!!");
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

  void initPlayerControls() {
    controlView = pView.findViewById(R.id.exo_controller);
    if (controlView != null) {
      pauseButton = controlView.findViewById(R.id.exo_pause);
      playButton = controlView.findViewById(R.id.exo_play);
    }
  }
}