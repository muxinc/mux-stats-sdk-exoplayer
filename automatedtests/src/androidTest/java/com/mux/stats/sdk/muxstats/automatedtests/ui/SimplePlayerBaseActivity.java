package com.mux.stats.sdk.muxstats.automatedtests.ui;

import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.muxstats.automatedtests.BuildConfig;
import com.mux.stats.sdk.muxstats.MuxStatsExoPlayer;
import com.mux.stats.sdk.muxstats.automatedtests.R;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.MockNetworkRequest;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class SimplePlayerBaseActivity extends AppCompatActivity implements
        PlaybackPreparer, Player.EventListener {

    static final String TAG = "SimplePlayerActivity";

    public static final String PLAYBACK_URL_KEY = "playback_url";
    public static final int PLAY_AUDIO_SAMPLE = 0;

    PlayerView playerView;
    SimpleExoPlayer player;
    MediaSource testMediaSource;
    MuxStatsExoPlayer muxStats;
    MockNetworkRequest mockNetwork;
    AtomicBoolean onResumedCalled = new AtomicBoolean(false);

    Lock activityLock = new ReentrantLock();
    Condition playbackEnded = activityLock.newCondition();
    Condition playbackStarted = activityLock.newCondition();
    Condition playbackBuffering = activityLock.newCondition();
    Condition activityClosed = activityLock.newCondition();
    Condition activityInitialized = activityLock.newCondition();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_player_test);

        disableUserActions();

        playerView = findViewById(R.id.player_view);
        playerView.setPlaybackPreparer(this);

        initExoPlayer();
        player.addListener(this);
        playerView.setPlayer(player);

        // Do not hide controlls
        playerView.setControllerShowTimeoutMs(0);
        playerView.setControllerHideOnTouch(false);

        initMuxSats();

        Intent i = getIntent();
        Bundle extra = i.getExtras();
        String url_to_play = "http://localhost:5000/vod.mp4";
        if (extra != null &&
                extra.containsKey(PLAYBACK_URL_KEY)) {
            switch(extra.getInt(PLAYBACK_URL_KEY)) {
                case PLAY_AUDIO_SAMPLE:
                    url_to_play = "http://localhost:5000/audio.aac";
                    break;
                default:
                    url_to_play = "http://localhost:5000/vod.mp4";
                    break;
            }
        }
        Uri testUri = Uri.parse(url_to_play);
        testMediaSource = new ExtractorMediaSource.Factory(
                new DefaultDataSourceFactory(this, "Test"))
                .createMediaSource(testUri);
        player.setPlayWhenReady(true);
        player.prepare(testMediaSource, false, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        onResumedCalled.set(true);
    }

    @Override
    public void onStop() {
        super.onStop();
        muxStats.release();
    }

    public abstract void initExoPlayer();

    public void initMuxSats() {
        // Mux details
        CustomerPlayerData customerPlayerData = new CustomerPlayerData();
        if (BuildConfig.SHOULD_REPORT_INSTRUMENTATION_TEST_EVENTS_TO_SERVER) {
            customerPlayerData.setEnvironmentKey(BuildConfig.INSTRUMENTATION_TEST_ENVIRONMENT_KEY);
        } else {
            customerPlayerData.setEnvironmentKey("YOUR_ENVIRONMENT_KEY");
        }
        CustomerVideoData customerVideoData = new CustomerVideoData();
        customerVideoData.setVideoTitle("Test video");
        mockNetwork = new MockNetworkRequest();
        muxStats = new MuxStatsExoPlayer(
                this, player, "demo-player", customerPlayerData, customerVideoData,
                null,true, mockNetwork);
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        muxStats.setScreenSize(size.x, size.y);
        muxStats.setPlayerView(playerView);
        muxStats.enableMuxCoreDebug(true, false);
    }

    public MediaSource getTestMediaSource() {
        return testMediaSource;
    }

    public PlayerView getPlayerView() {
        return playerView;
    }

    public MockNetworkRequest getMockNetwork() {
        return mockNetwork;
    }

    public void waitForPlaybackToFinish() {
        try {
            activityLock.lock();
            playbackEnded.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            activityLock.unlock();
        }
    }

    public void waitForActivityToInitialize() {
        if (!onResumedCalled.get()) {
            try {
                activityLock.lock();
                activityInitialized.await();
                activityLock.unlock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean waitForPlaybackToStart(long timeoutInMs) {
        try {
            activityLock.lock();
            return playbackStarted.await(timeoutInMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        } finally {
            activityLock.unlock();
        }
    }

    public void waitForPlaybackToStartBuffering() {
        if (player.getPlaybackState() == Player.STATE_READY &&
            player.getPlayWhenReady()) {
            try {
                activityLock.lock();
                playbackBuffering.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                activityLock.unlock();
            }
        }
    }

    public void waitForActivityToClose() {
        try {
            activityLock.lock();
            activityClosed.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            activityLock.unlock();
        }
    }

    public void signalPlaybackStarted() {
        activityLock.lock();
        playbackStarted.signalAll();
        activityLock.unlock();
    }

    public void signalPlaybackBuffering() {
        activityLock.lock();
        playbackBuffering.signalAll();
        activityLock.unlock();
    }

    public void signalPlaybackEnded() {
        activityLock.lock();
        playbackEnded.signalAll();
        activityLock.unlock();
    }

    public void signalActivityClosed() {
        activityLock.lock();
        activityClosed.signalAll();
        activityLock.unlock();
    }

    private void disableUserActions() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private void enableUserActions() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        signalActivityClosed();
    }

    ///////////////////////////////////////////////////////////////////////
    ///////// PlaybackPreparer ////////////////////////////////////////////

    @Override
    public void preparePlayback() {
//        player.retry();
    }

    //////////////////////////////////////////////////////////////////////
    ////// Player.EventListener //////////////////////////////////////////

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                signalPlaybackBuffering();
                break;
            case Player.STATE_ENDED:
                signalPlaybackEnded();
                break;
            case Player.STATE_READY:
                // By the time we get here, it depends on playWhenReady to know if we're playing
                if (playWhenReady) {
                    signalPlaybackStarted();
                } else {
                    // TODO implement this
//                    signalPlaybackPaused();
                }
                break;
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        activityLock.lock();
        activityInitialized.signalAll();
        activityLock.unlock();
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.e(TAG, error.getMessage());
        error.printStackTrace();
    }

    @Override
    public void onPositionDiscontinuity(int reason) {

    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    @Override
    public void onSeekProcessed() {

    }
}
