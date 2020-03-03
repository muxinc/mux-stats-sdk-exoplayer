package com.mux.stats.sdk.muxstats;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.mux.stats.sdk.R;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;

public class SimplePlayerTestActivity extends AppCompatActivity implements PlaybackPreparer, Player.EventListener {

    static final String TAG = "SimplePlayerTestActivity";

    public PlayerView playerView;
    public SimpleExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_player_test);

        playerView = findViewById(R.id.player_view);
        playerView.setPlaybackPreparer(this);

        RenderersFactory renderersFactory = buildRenderersFactory(false);
        TrackSelection.Factory trackSelectionFactory = new RandomTrackSelection.Factory();
        TrackSelector trackSelector = new DefaultTrackSelector(trackSelectionFactory);

        // For 2.9.6 and higher
        player =
                ExoPlayerFactory.newSimpleInstance(this,
                        renderersFactory,
                        trackSelector);

//        player =
//                ExoPlayerFactory.newSimpleInstance(
//                        renderersFactory,
//                        trackSelector);
        player.addListener(this);
        playerView.setPlayer(player);

//        Uri testUri = Uri.parse("https://html5demos.com/assets/dizzy.mp4");
        Uri testUri = Uri.parse("http://vod.leasewebcdn.com/bbb.flv?ri=1024&rs=150&start=0");
        MediaSource testMediaSource = new ProgressiveMediaSource.Factory(buildDataSourceFactory())
                .createMediaSource(testUri);
//        MediaSource testMediaSource = new ExtractorMediaSource.Factory(buildDataSourceFactory()).createMediaSource(testUri);

//        Handler lHandler = new Handler();

//        lHandler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                initMuxSats();
//            }
//        }, 9000);

//        initMuxSats();
//        player.setPlayWhenReady(true);
        player.setPlayWhenReady(false);
        player.prepare(testMediaSource, false, false);
    }

    private RenderersFactory buildRenderersFactory(boolean preferExtensionRenderer) {
//        return new DefaultRenderersFactory(/* context= */ this)
//                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);

        return new DefaultRenderersFactory(/* context= */ this);
    }

    private DataSource.Factory buildDataSourceFactory() {
        return new DefaultDataSourceFactory(this, "Test");
    }

    @Override
    public void preparePlayback() {
//        player.retry();
    }

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

    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

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
