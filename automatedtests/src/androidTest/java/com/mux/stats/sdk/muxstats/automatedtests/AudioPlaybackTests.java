package com.mux.stats.sdk.muxstats.automatedtests;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.fail;

public class AudioPlaybackTests extends SeekingTestBase {

  @Before
  public void init() {
    urlToPlay = "http://localhost:5000/audio.aac";
    super.init();
  }

  @Test
  public void testSeekingWhilePausedAudioOnly() {
    testSeekingWhilePaused();
  }

  @Test
  public void testSeekingWhilePlayingAudioOnly() {
    testSeekingWhilePlaying();
  }

}
