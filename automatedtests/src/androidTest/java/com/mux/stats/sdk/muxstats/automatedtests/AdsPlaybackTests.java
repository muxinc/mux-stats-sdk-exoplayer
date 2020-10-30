package com.mux.stats.sdk.muxstats.automatedtests;

import android.os.Bundle;

import com.mux.stats.sdk.core.events.playback.AdBreakStartEvent;
import com.mux.stats.sdk.core.events.playback.AdEndedEvent;
import com.mux.stats.sdk.core.events.playback.AdPauseEvent;
import com.mux.stats.sdk.core.events.playback.AdPlayEvent;
import com.mux.stats.sdk.core.events.playback.AdPlayingEvent;
import com.mux.stats.sdk.core.events.playback.PauseEvent;
import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.muxstats.automatedtests.ui.SimplePlayerBaseActivity;

import org.junit.Test;

import static org.junit.Assert.fail;

public class AdsPlaybackTests extends TestBase {

    static final int PREROLL_AD_PERIOD =  10000;
    static final int BUMPERL_AD_PERIOD =  5000;

    public Bundle getActivityOptions() {
        Bundle b = new Bundle();
        b.putInt(SimplePlayerBaseActivity.PLAYBACK_URL_KEY,
                SimplePlayerBaseActivity.PLAY_PRE_ROLL_AND_BUMPER_SAMPLE);
        return b;
    }

    @Test
    public void testPreRollAndBumperAds() {
        try {
            if(!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
                fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
            }
            // First ad is 10 second
            Thread.sleep(PREROLL_AD_PERIOD / 2);
            // Pause the ad for 5 seconds
            pausePlayer();
            Thread.sleep(PAUSE_PERIOD_IN_MS);
            // resume the ad playback
            resumePlayer();
            Thread.sleep(PREROLL_AD_PERIOD + BUMPERL_AD_PERIOD);

            // Check ad start event
            int playIndex = networkRequest.getIndexForFirstEvent(PlayEvent.TYPE);
            int pauseIndex = networkRequest.getIndexForFirstEvent(PauseEvent.TYPE);
            int adBreakstartIndex = networkRequest.getIndexForFirstEvent(AdBreakStartEvent.TYPE);
            int adPlayIndex = networkRequest.getIndexForFirstEvent(AdPlayEvent.TYPE);
            int adPlayingIndex = networkRequest.getIndexForFirstEvent(AdPlayingEvent.TYPE);

            if (playIndex == -1 || pauseIndex == -1
                    || adBreakstartIndex == -1 || adPlayIndex == -1 || adPlayingIndex == -1) {
                fail("Missing basic start events ! playIndex: " + playIndex +
                        ", pauseIndex: " + pauseIndex + ", adBreakStartIndex: " +
                        adBreakstartIndex + ", adPlayIndex: " + adPlayIndex + ", adPlayingIndex: "
                        + adPlayingIndex);
            }
            if (!(playIndex  < pauseIndex && pauseIndex < adBreakstartIndex
                    && adBreakstartIndex < adPlayIndex && adPlayIndex < adPlayingIndex) ) {
                fail("Basic start events not ordered correctly ! playIndex: " + playIndex +
                         ", pauseIndex: " + pauseIndex + ", adBreakStartIndex: " +
                        adBreakstartIndex + ", adPlayIndex: " + adPlayIndex + ", adPlayingIndex: "
                        + adPlayingIndex);
            }

            // Check first ad play period
            int adPauseIndex = networkRequest.getIndexForFirstEvent(AdPauseEvent.TYPE);
            long firstAdPlayPeriod = networkRequest.getCreationTimeForEvent(adPauseIndex) -
                    networkRequest.getCreationTimeForEvent(adPlayingIndex);
            long expectedFirstAdPlayPeriod = PREROLL_AD_PERIOD / 2;
            if (Math.abs(firstAdPlayPeriod - expectedFirstAdPlayPeriod) > 500) {
                fail("First ad play period do not match expected play period, reported: " +
                        firstAdPlayPeriod + ", expected ad play period: " + expectedFirstAdPlayPeriod);
            }

            // Check ad Pause
            adPlayIndex = networkRequest.getIndexForNextEvent(adPauseIndex, AdPlayEvent.TYPE);
            long firstAdPausePeriod = networkRequest.getCreationTimeForEvent(adPlayIndex) -
                    networkRequest.getCreationTimeForEvent(adPauseIndex);
            if (Math.abs(firstAdPausePeriod - PAUSE_PERIOD_IN_MS) > 500) {
                fail("First ad pause period do not match expected pause period, reported pause period: " +
                        firstAdPausePeriod + ", expected ad pause period: " + PAUSE_PERIOD_IN_MS);
            }

            // Check rest of the first ad playback
            int adEndedIndex = networkRequest.getIndexForNextEvent(adPlayIndex, AdEndedEvent.TYPE);
            firstAdPlayPeriod = networkRequest.getCreationTimeForEvent(adEndedIndex) -
                    networkRequest.getCreationTimeForEvent(adPlayIndex);
            if (Math.abs(firstAdPlayPeriod - expectedFirstAdPlayPeriod) > 500) {
                fail("First ad play period do not match expected play period, reported: " +
                        firstAdPlayPeriod + ", expected ad play period: " + expectedFirstAdPlayPeriod);
            }

            // Check bumper ad
            adPlayingIndex = networkRequest.getIndexForNextEvent(adEndedIndex, AdPlayingEvent.TYPE);
            adEndedIndex = networkRequest.getIndexForNextEvent(adPlayingIndex, AdEndedEvent.TYPE);
            long bumperAdPlayPeriod = networkRequest.getCreationTimeForEvent(adEndedIndex) -
                    networkRequest.getCreationTimeForEvent(adPlayingIndex);
            if (Math.abs(bumperAdPlayPeriod - BUMPERL_AD_PERIOD) > 500) {
                fail("Bumper ad period do not match expected bumper period, reported: " +
                        bumperAdPlayPeriod + ", expected ad play period: " + BUMPERL_AD_PERIOD);
            }

            // Check content play resume events
            playIndex = networkRequest.getIndexForNextEvent(adEndedIndex, PlayEvent.TYPE);
            int playingIndex = networkRequest.getIndexForNextEvent(adEndedIndex, PlayingEvent.TYPE);
            if (playIndex == -1 || playingIndex == -1) {
                fail("Missing play and playing events after adBreak");
            }
            if (playIndex >= playingIndex) {
                fail("Play events after ad break are not ordered correctly");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
