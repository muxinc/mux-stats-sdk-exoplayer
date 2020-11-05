package com.mux.stats.sdk.muxstats.automatedtests;

import org.junit.Before;
import org.junit.Test;

public class RenditionChangeTests extends TestBase {


    @Before
    public void init(){
        urlToPlay = "http://localhost:5000/hls/google_glass/playlist.m3u8";
        bandwidthLimitInBitsPerSecond = 4500000;
        super.init();
    }

    @Test
    public void testRenditionChange() {
        try {
            Thread.sleep(100000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
