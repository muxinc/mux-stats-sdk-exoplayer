package com.mux.stats.sdk.muxstats.automatedtests;

import org.junit.Before;
import org.junit.Test;

public class AudioPlaybackTests extends SeekingTestBase {

  @Before
  public void init() {
    bandwidthLimitInBitsPerSecond = 150000;
    urlToPlay = "http://localhost:5000/audio.mp4";
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
