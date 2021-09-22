package com.mux.stats.sdk.muxstats.automatedtests;

import static org.junit.Assert.fail;

import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.RebufferStartEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RunInBackgroundTests extends TestBase {

  @Before
  public void init() {
    urlToPlay = "http://localhost:5000/audio.mp4";
    super.init();
  }

  @Test
  public void testAudioVodPlaybackInBackground() {
    testVodPlayback(true,10000, 72000, 1500);
  }
}
