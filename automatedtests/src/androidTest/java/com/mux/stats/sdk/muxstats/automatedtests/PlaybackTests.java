package com.mux.stats.sdk.muxstats.automatedtests;

import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.RebufferEndEvent;
import com.mux.stats.sdk.core.events.playback.RebufferStartEvent;
import com.mux.stats.sdk.core.events.playback.ViewStartEvent;
import com.mux.stats.sdk.muxstats.R;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.Event;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.MockNetworkRequest;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import static org.junit.Assert.fail;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class PlaybackTests extends TestBase {

    public static final String TAG = "playbackTest";

    static final int PLAY_PERIOD_IN_MS = 10000;
    static final int PAUSE_PERIOD_IN_MS = 3000;
    static final int EVENT_MAX_TIME_DIFF_MS = 100;


    /*
     * Check if given events are dispatched in correct order and timestamp
     */
    private void checkEvents(ArrayList<Event> eventsOrder, MockNetworkRequest networkRequest) throws JSONException {
        int lookingForEventAtIndex = 0;

        for (int i = 0; i < networkRequest.getNumberOfReceivedEvents(); i++ ) {
            String receivedEventName = networkRequest.getReceivedEventName(i);
            if (receivedEventName.equals(eventsOrder.get(lookingForEventAtIndex).getName())) {
                lookingForEventAtIndex++;
            }
            if (lookingForEventAtIndex >= eventsOrder.size()) {
                return;
            }
        }

        ArrayList<String> eventsOrderNames = new ArrayList<>();
        for (int i = 0; i < eventsOrder.size(); i++) {
            eventsOrderNames.add(eventsOrder.get(i).getName());
        }

        String failMessage = "Received events not in a correct order:\n";
        failMessage += "Expected: " + eventsOrderNames + " \n";
        failMessage += "Received: " + networkRequest.getReceivedEventNames();
        fail(failMessage);
    }

    /*
     * According to the self validation guid: https://docs.google.com/document/d/1FU_09N3Cg9xfh784edBJpgg3YVhzBA6-bd5XHLK7IK4/edit#
     * We are implementing vod playback scenario.
     */
    @Test
    public void testVodPlayback() {
        try {
            if(!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
                fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
            }

            // Init player controlls
            controlView = pView.findViewById(R.id.exo_controller);
            if (controlView != null) {
                pauseButton = controlView.findViewById(R.id.exo_pause);
                playButton = controlView.findViewById(R.id.exo_play);
            }

            ArrayList<Event> expectedEvents = new ArrayList<>();

            // play x seconds
            Thread.sleep(PLAY_PERIOD_IN_MS);
            // check player init events
            expectedEvents.add(new Event("playerready"));
            expectedEvents.add(new Event("viewstart"));
            expectedEvents.add(new Event("play"));
            expectedEvents.add(new Event("playing"));

            pausePlayer();

            // check player pause event
            expectedEvents.add(new Event("pause"));

            Thread.sleep(PAUSE_PERIOD_IN_MS);
            // Resume video

            resumePlayer();

            /// check player play event
            expectedEvents.add(new Event("play"));
            expectedEvents.add(new Event("playing"));

            // Play another x seconds
            Thread.sleep(PLAY_PERIOD_IN_MS);

            // Seek backward
            testActivity.runOnUiThread(new Runnable(){
                public void run() {
                    long currentPlaybackPosition = pView.getPlayer().getCurrentPosition();
                    pView.getPlayer().seekTo(currentPlaybackPosition/2);
                }
            });

            // Play another x seconds
            Thread.sleep(PLAY_PERIOD_IN_MS);

            // check player seek event
            expectedEvents.add(new Event("pause"));
            expectedEvents.add(new Event("seeking"));
            expectedEvents.add(new Event("seeked"));
            expectedEvents.add(new Event("playing"));

            // seek forward in the video
            testActivity.runOnUiThread(new Runnable(){
                public void run() {
                    long currentPlaybackPosition = pView.getPlayer()
                            .getCurrentPosition();
                    long videoDuration = pView.getPlayer().getDuration();
                    long seekToInFuture = currentPlaybackPosition + ((videoDuration - currentPlaybackPosition) / 2);
                    pView.getPlayer().seekTo(seekToInFuture);
                }
            });

            // Play another x seconds
            Thread.sleep(PLAY_PERIOD_IN_MS);

            // check player seek event
            expectedEvents.add(new Event("pause"));
            expectedEvents.add(new Event("seeking"));
            expectedEvents.add(new Event("seeked"));
            expectedEvents.add(new Event("playing"));

            // Exit the player with back button
            exitActivity();
            testActivity.waitForActivityToClose();
            Log.w(TAG, "See what event should be dispatched on view closed !!!");
            // TODO check player end event
            checkEvents(expectedEvents, networkRequest);
        } catch (InterruptedException e) {
            e.printStackTrace();
            // fail test
            fail();
        } catch (JSONException e) {
            e.printStackTrace();
            fail();
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

            // play x seconds
            if(!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
                fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
            }

            long measuredRebufferPeriod = System.currentTimeMillis() - rebufferStartedAT;
            Thread.sleep(networkJamPeriodInMs);
            exitActivity();

            // Startup time check
            int viewstartIndex = networkRequest.getIndexForFirstEvent(ViewStartEvent.TYPE);
            int playingIndex = networkRequest.getIndexForFirstEvent(PlayingEvent.TYPE);
            // Check if viewstart and playing events are received
            if (viewstartIndex == -1) {
                fail("viewstartIndex event not received !!!");
            }
            if (playingIndex == -1) {
                fail("playingIndex event not received !!!");
            }

            long reportedStartupTime = networkRequest.getCreationTimeForEvent(playingIndex) -
                    networkRequest.getCreationTimeForEvent(viewstartIndex);
            // Check if startup time match with in 200 ms precission
            if (Math.abs(reportedStartupTime - expectedStartupTime) > 500) {
                fail("Reported startup time and expected startup time do not match within 500 ms,"
                        + "reported time: " + reportedStartupTime + ", measured startup time: " + expectedStartupTime);
            }

            // check rebuffering events
            int rebufferStartEventIndex = networkRequest.getIndexForFirstEvent(RebufferStartEvent.TYPE);
            int rebufferEndEventIndex = networkRequest.getIndexForFirstEvent(RebufferEndEvent.TYPE);
            // Check if rebuffer events are received
            if (rebufferStartEventIndex == -1) {
                fail("rebufferstart event not received !!!");
            }
            if(rebufferEndEventIndex == -1) {
                fail("rebufferend event not received !!!");
            }

//            long jamedNetworkBitrate = (long)((double)bandwidthLimitInBitsPerSecond / (double)networkJamFactor);
//            long bitsReceivedWhileJammed = (long)(jamedNetworkBitrate * ((double)networkJamPeriodInMs / 1000.0));
//            long requiredBitsForPlaybackPeriod = (long)(sampleFileBitrate * ((double)networkJamPeriodInMs / 1000.0));
//
//            long timeReceivedWhileJammed = (long)(bitsReceivedWhileJammed / (double)(sampleFileBitrate) * 1000.0);
//            long missingVideoTimeInMs = (long)(((double)(requiredBitsForPlaybackPeriod - bitsReceivedWhileJammed) /
//                    (double)(sampleFileBitrate)) * 1000.0);
//            long estimatedRebufferPeriod = networkJamPeriodInMs - (bufferedTime - reportedStartupTime);

//            long estimatedRebufferPeriod = (long)((double)missingVideoTimeInMs /
//                    ( (double)bandwidthLimitInBitsPerSecond / (double) sampleFileBitrate) )
//                    - (bufferedTime - reportedStartupTime);

            long rebufferStartedAt = networkRequest.getCreationTimeForEvent(rebufferStartEventIndex);
            long rebufferEndedAt = networkRequest.getCreationTimeForEvent(rebufferEndEventIndex);
            long rebufferPeriod = rebufferEndedAt - rebufferStartedAt;

            // TODO see what is the best way to calculate this check
//            if (Math.abs(rebufferPeriod - measuredRebufferPeriod) > 400) {
//                fail("Calculated rebuffer time do not match reported rebuffer time with in 400 ms range");
//            }
        } catch (JSONException e) {
            e.printStackTrace();
            fail();
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }
    }
}