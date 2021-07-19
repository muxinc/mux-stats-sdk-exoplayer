package com.mux.stats.sdk.muxstats.automatedtests;

import static org.junit.Assert.fail;

import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.SeekedEvent;
import com.mux.stats.sdk.core.events.playback.SeekingEvent;
import org.junit.Before;
import org.junit.Test;

public class SeekingTests extends SeekingTestBase {

  int contentDurationInMs = 128267;

  @Before
  public void init() {
    if (currentTestName.getMethodName()
        .equalsIgnoreCase("testPlaybackWhenStartingFromThePosition")) {
      playbackStartPosition = contentDurationInMs / 3;
    }
    if (currentTestName.getMethodName()
        .equalsIgnoreCase("testSeekWhilePlayWhenReadyIsFalse")) {
      playbackStartPosition = contentDurationInMs / 3;
      playWhenReady = false;
    }
    super.init();
  }

  /*
   * Test Seeking, event order
   */
  @Test
  public void testSeekingWhilePausedVideoAndAudio() {
    testSeekingWhilePaused();
  }

  @Test
  public void testSeekingWhilePlayingVideoAndAudio() {
    testSeekingWhilePlaying();
  }

  @Test
  public void testSeekWhilePlayWhenReadyIsFalse() {
    try {
      testActivity.waitForActivityToInitialize();
      Thread.sleep(PLAY_PERIOD_IN_MS);
      int playIndex = networkRequest.getIndexForFirstEvent(PlayEvent.TYPE);
      int playingIndex = networkRequest.getIndexForFirstEvent(PlayingEvent.TYPE);
      int seekingIndex = networkRequest.getIndexForFirstEvent(SeekingEvent.TYPE);
      int seekedIndex = networkRequest.getIndexForFirstEvent(SeekedEvent.TYPE);
      if (playIndex != -1 || playingIndex != -1) {
        fail("Playback events present, this is major error: playIndex: "
            + playIndex + ", playingIndex: " + playingIndex);
      }
      if (seekingIndex == -1 || seekedIndex == -1) {
        fail("Seeking events missing: seekingIndex: "
            + seekedIndex + ", seekingIndex: " + seekingIndex);
      }
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }

  /*
   * We are currently missing a play event in this use case scenario
   */
  @Test
  public void testPlaybackWhenStartingFromThePosition() {
    try {
      if (!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
        fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
      }
      Thread.sleep(WAIT_FOR_NETWORK_PERIOD_IN_MS);
      int playIndex = networkRequest.getIndexForFirstEvent(PlayEvent.TYPE);
      int playingIndex = networkRequest.getIndexForFirstEvent(PlayingEvent.TYPE);
      if (playIndex == -1 || playingIndex == -1) {
        fail("Missing basic playback events: playIndex: "
            + playIndex + ", playingIndex: " + playingIndex);
      }
      if (playingIndex < playIndex) {
        fail("Playback events not ordered correctly: playIndex: "
            + playIndex + ", playingIndex: " + playingIndex);
      }
      // TODO see if we need to capture the seeking events too
    } catch (Exception e) {
      fail(getExceptionFullTraceAndMessage(e));
    }
  }
}
