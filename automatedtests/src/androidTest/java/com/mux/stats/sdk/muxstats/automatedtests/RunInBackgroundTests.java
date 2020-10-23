package com.mux.stats.sdk.muxstats.automatedtests;

import android.os.Bundle;

import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.muxstats.automatedtests.ui.SimplePlayerBaseActivity;

import org.junit.Test;

import static org.junit.Assert.fail;

public class RunInBackgroundTests extends TestBase {

    public Bundle getActivityOptions() {
        Bundle b = new Bundle();
        b.putInt(SimplePlayerBaseActivity.PLAYBACK_URL_KEY,
                SimplePlayerBaseActivity.PLAY_AUDIO_SAMPLE);
        return b;
    }

    @Test
    public void testBackgroundAudioPlayback() {
        try {
            testActivity.waitForActivityToInitialize();
            backgroundActivity();
            testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS);
            long playbackStartedAt = System.currentTimeMillis();
            Thread.sleep(PLAY_PERIOD_IN_MS);
            int playingEventIndex = networkRequest.getNumberOfReceivedEvents() - 1;
            if (!networkRequest.getReceivedEventName(playingEventIndex).equalsIgnoreCase(PlayingEvent.TYPE)) {
                fail("Last received event must be playing ! Received: " + networkRequest.getReceivedEventNames());
            }
            // Find preceding playing event
            int playEventIndex = playingEventIndex - 2;
            while (playEventIndex > 0) {
                if (networkRequest.getReceivedEventName(playingEventIndex).equalsIgnoreCase(PlayEvent.TYPE)) {
                    break;
                }
                playEventIndex --;
            }
            if (playEventIndex == 0) {
                fail("Playing event missing ! Received: " + networkRequest.getReceivedEventNames());
            }
            long timeDiff = networkRequest.getCreationTimeForEvent(playEventIndex) -
                    networkRequest.getCreationTimeForEvent(playingEventIndex);
            if (timeDiff > 500) {
                fail("Playing event is more then 500 ms apart from play event !!!");
            }
            // Check playback time
            long reportedPlaybackTime = System.currentTimeMillis() - networkRequest.getCreationTimeForEvent(playEventIndex);
            long measuredPlaybackTime = System.currentTimeMillis() - playbackStartedAt;
            timeDiff = Math.abs(reportedPlaybackTime - measuredPlaybackTime);
            if (timeDiff > 500) {
                fail("Reported playback time is " + timeDiff + " ms apart from measured playback time !!!");
            }
            testScenario.close();
            testActivity.waitForActivityToClose();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
