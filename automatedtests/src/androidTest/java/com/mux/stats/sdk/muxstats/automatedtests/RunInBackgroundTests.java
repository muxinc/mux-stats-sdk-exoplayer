package com.mux.stats.sdk.muxstats.automatedtests;

import android.os.Bundle;

import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.RebufferStartEvent;
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
            Thread.sleep(PLAY_PERIOD_IN_MS);
            int playingEventIndex = networkRequest.getIndexForFirstEvent(PlayingEvent.TYPE);
            if (playingEventIndex == -1) {
                fail("Playing event missing ! Received : " + networkRequest.getReceivedEventNames());
            }
            int playEventIndex = networkRequest.getIndexForFirstEvent(PlayEvent.TYPE);
            if (playEventIndex == -1) {
                fail("Play event missing ! Received : " + networkRequest.getReceivedEventNames());
            }
            if (playEventIndex > playingEventIndex) {
                fail("Play event came after Playing event ! Received : " + networkRequest.getReceivedEventNames());
            }
            int rebufferEventIndex = networkRequest.getIndexForFirstEvent(RebufferStartEvent.TYPE);
            if (rebufferEventIndex != -1) {
                fail("Got rebuffer event on a smooth playback ! Received : " + networkRequest.getReceivedEventNames());
            }
            long timeDiff = networkRequest.getCreationTimeForEvent(playEventIndex) -
                    networkRequest.getCreationTimeForEvent(playingEventIndex);
            if (timeDiff > 500) {
                fail("Playing event is more then 500 ms apart from play event !!!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
