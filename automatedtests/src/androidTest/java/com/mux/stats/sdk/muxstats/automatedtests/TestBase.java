package com.mux.stats.sdk.muxstats.automatedtests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.UiDevice;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.MockNetworkRequest;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.http.SimpleHTTPServer;
import com.mux.stats.sdk.muxstats.automatedtests.ui.SimplePlayerTestActivity;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.io.IOException;
import java.util.Collection;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.fail;

public abstract class TestBase {

    @Rule
    public ActivityScenarioRule<SimplePlayerTestActivity> activityRule =
            new ActivityScenarioRule(new Intent(
                    ApplicationProvider.getApplicationContext(),
                    SimplePlayerTestActivity.class).putExtras(getActivityOptions())
            );

    @Rule public TestName currentTestName = new TestName();

    static final int PLAY_PERIOD_IN_MS = 10000;
    static final int PAUSE_PERIOD_IN_MS = 3000;
    protected int runHttpServerOnPort = 5000;
    protected int bandwidthLimitInBitsPerSecond = 1500000;
    protected int sampleFileBitrate = 1083904;
    // UTC timestamp whenlow network bandwidth was triggered
    long startedJammingTheNetworkAt;
    // Amount of video playback time in player buffer
    private long bufferedTime;
    protected int networkJamPeriodInMs = 10000;
    // This is the number of times the network bandwidth will be reduced,
    // not constantly but each 10 ms a random number between 2 and factor will divide
    // the regular amount of bytes to send.
    // This will stop server completly, this will allow us to easier calculate the rebuffer period
    protected int networkJamFactor = 4;
    protected int waitForPlaybackToStartInMS = 10000;

    protected ActivityScenario<SimplePlayerTestActivity> testScenario;
    protected SimplePlayerTestActivity testActivity;
    protected Activity currentActivity;
    protected SimpleHTTPServer httpServer;
    protected PlayerView pView;
    protected MediaSource testMediaSource;
    protected MockNetworkRequest networkRequest;

    PlayerControlView controlView;
    View pauseButton;
    View playButton;


    @Before
    public void init(){
        try {
            httpServer = new SimpleHTTPServer(runHttpServerOnPort, bandwidthLimitInBitsPerSecond);
        } catch (IOException e) {
            e.printStackTrace();
            // Failed to start server
            fail("Failed to start HTTP server, why !!!");
        }
        try {
            testActivity = (SimplePlayerTestActivity) getActivityInstance();
        } catch (ClassCastException e) {
            fail("Got wrong activity instance in test init !!!");
        }
        if (testActivity == null) {
            fail("Test activity not found !!!");
        }
        testActivity.runOnUiThread(() -> {
            testActivity.setVideoTitle(currentTestName.getMethodName());
            testActivity.initMuxSats();
            testActivity.startPlayback();
        });
        testScenario = activityRule.getScenario();
        pView = testActivity.getPlayerView();
        testMediaSource = testActivity.getTestMediaSource();
        networkRequest = testActivity.getMockNetwork();
    }

    @After
    public void cleanup() {
        if (httpServer != null) {
            httpServer.kill();
        }
        testScenario.close();
    }

    public abstract Bundle getActivityOptions();

    public void jamNetwork() {
        testActivity.runOnUiThread(() -> {
            startedJammingTheNetworkAt = System.currentTimeMillis();
            long bufferPosition = pView.getPlayer().getBufferedPosition();
            long currentPosition = pView.getPlayer().getCurrentPosition();
            bufferedTime = bufferPosition - currentPosition;
            httpServer.jamNetwork(networkJamPeriodInMs, networkJamFactor, true);
        });
    }

    public void exitActivity() {
        testActivity.runOnUiThread(() -> testActivity.finish());
    }

    public void pausePlayer() {
        // Pause video
        testActivity.runOnUiThread(() -> {
            if (pauseButton != null) {
                pauseButton.performClick();
            } else {
                pView.getPlayer().stop();
            }
        });
    }

    public void resumePlayer() {
        testActivity.runOnUiThread(() -> {
            if (playButton != null) {
                playButton.performClick();
            } else {
                SimpleExoPlayer player = ((SimpleExoPlayer)pView.getPlayer());
                player.prepare(testMediaSource, false, false);
            }
        });
    }

    public void backgroundActivity() {
        UiDevice device = UiDevice.getInstance(getInstrumentation());
        device.pressHome();
    }

    private Activity getActivityInstance(){
        getInstrumentation().runOnMainSync(() -> {
            Collection<Activity> resumedActivities =
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
            for (Activity activity: resumedActivities){
                currentActivity = activity;
                break;
            }
        });

        return currentActivity;
    }
}
