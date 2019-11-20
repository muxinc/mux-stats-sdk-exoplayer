package com.mux.stats.sdk.muxstats;

import android.content.Context;
import android.graphics.Point;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;


public class MuxStatsTest {

    private MuxStatsExoPlayer muxStats;
    private SimpleExoPlayer player;
    private PlayerView playerView;

    @Before
    public void setUp() {
        CustomerPlayerData customerPlayerData = new CustomerPlayerData();
        customerPlayerData.setEnvironmentKey("YOUR_ENVIRONMENT_KEY");
        CustomerVideoData customerVideoData = new CustomerVideoData();
        customerVideoData.setVideoTitle("TestTitle");
        // Make sure we are starting with a fresh player!
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        muxStats = new MuxStatsExoPlayer(
                InstrumentationRegistry.getInstrumentation().getContext(),
                player,
                "demo-player",
                customerPlayerData,
                customerVideoData);
        Point size = new Point(1920, 1280);
        muxStats.setScreenSize(size.x, size.y);
        muxStats.setPlayerView(playerView);
    }

    @Test
    public void late_init_test() {
        assertTrue(false);
    }

}