package com.mediasolution4you.muxstatslateinit;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.EventLogger;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.muxstats.MuxStatsExoPlayer;
import com.mux.stats.sdk.core.util.MuxLogger;

import java.net.URI;

public class MainActivity extends AppCompatActivity implements
        PlayerControlView.VisibilityListener, PlaybackPreparer, View.OnClickListener {

    static final String TAG = "MainActivity";

    private LinearLayout debugRootView;
    private Button selectTracksButton;

    private PlayerView playerView;
    private SimpleExoPlayer player;
    private MediaSource mediaSource;
    private MuxStatsExoPlayer muxStats;

    boolean startAutoPlay = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Dash URI
        Uri uri = Uri.parse("https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd");

        mediaSource =  new DashMediaSource.Factory(
                new DefaultDataSourceFactory(
                        this,
                        new DefaultHttpDataSourceFactory("Android"))).createMediaSource(uri);


        debugRootView = findViewById(R.id.controls_root);
        selectTracksButton = findViewById(R.id.select_tracks_button);
        selectTracksButton.setOnClickListener(this);

        playerView = findViewById(R.id.player_view);
        playerView.setControllerVisibilityListener(this);
        playerView.setErrorMessageProvider(new PlayerErrorMessageProvider());
        playerView.requestFocus();

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(
                new AdaptiveTrackSelection.Factory());
        trackSelector.setParameters(new DefaultTrackSelector.ParametersBuilder().build());

        player =
                ExoPlayerFactory.newSimpleInstance(
                        /* context= */ this,
                        buildRenderersFactory(),
                        trackSelector);
        player.addListener(new PlayerEventListener());
        player.setPlayWhenReady(startAutoPlay);
        player.addAnalyticsListener(new EventLogger(trackSelector));
        playerView.setPlayer(player);
        playerView.setPlaybackPreparer(this);


        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                CustomerPlayerData customerPlayerData = new CustomerPlayerData();
                customerPlayerData.setEnvironmentKey("YOUR ENVIRONMENT KEY HERE");
                CustomerVideoData customerVideoData = new CustomerVideoData();
                customerVideoData.setVideoTitle("Test video tittle");

                MuxLogger.setAllowLogcat(true, false);
                muxStats = new MuxStatsExoPlayer(
                        MainActivity.this,
                        player,
                        "demo-player",
                        customerPlayerData,
                        customerVideoData);
                Point size = new Point();
                getWindowManager().getDefaultDisplay().getSize(size);
                muxStats.setScreenSize(size.x, size.y);
                muxStats.setPlayerView(playerView);

            }
        }, 3000);
        player.prepare(mediaSource, false, false);
    }

    @Override
    public void onVisibilityChange(int visibility) {
        debugRootView.setVisibility(visibility);
    }

    public RenderersFactory buildRenderersFactory() {
        @DefaultRenderersFactory.ExtensionRendererMode
        int extensionRendererMode =
                         DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
        return new DefaultRenderersFactory(/* context= */ this)
                .setExtensionRendererMode(extensionRendererMode);
    }

    private void updateButtonVisibility() {

    }

    private void showControls() {
        debugRootView.setVisibility(View.VISIBLE);
    }

    @Override
    public void preparePlayback() {
        player.retry();
    }

    @Override
    public void onClick(View v) {
        Toast.makeText(this, "No idea what to do here !!!", Toast.LENGTH_LONG).show();
    }

    private class PlayerEventListener implements Player.EventListener {

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            switch (playbackState) {
                case Player.STATE_IDLE:
                    Log.i(TAG, "Player idle ...");
                    break;
                case Player.STATE_BUFFERING:
                    Log.i(TAG, "Player buffering ...");
                    break;
                case Player.STATE_READY:
                    Log.i(TAG, "Player ready ...");
                    break;
                case Player.STATE_ENDED:
                    Log.i(TAG, "Player ended ...");
                    break;
            }

            if (playbackState == Player.STATE_ENDED) {
                showControls();
            }
            updateButtonVisibility();
        }

        @Override
        public void onPlayerError(ExoPlaybackException e) {
            Log.e(TAG, e.getMessage());
        }

        @Override
        @SuppressWarnings("ReferenceEquality")
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
            // What to do here !!!
            Log.i(TAG, "Track changed !!!");
        }
    }

    private class PlayerErrorMessageProvider implements ErrorMessageProvider<ExoPlaybackException> {

        @Override
        public Pair<Integer, String> getErrorMessage(ExoPlaybackException e) {
            String errorString = "Error generic";
            if (e.type == ExoPlaybackException.TYPE_RENDERER) {
                Exception cause = e.getRendererException();
                if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                    // Special case for decoder initialization failures.
                    MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
                            (MediaCodecRenderer.DecoderInitializationException) cause;
                    if (decoderInitializationException.decoderName == null) {
                        if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                            errorString = "Error querying decoder";
                        } else if (decoderInitializationException.secureDecoderRequired) {
                            errorString = "Error no secure decoder";
                        } else {
                            errorString = "Error no decoder";
                        }
                    } else {
                        errorString = "Error instansiating decoder";
                    }
                }
            }
            return Pair.create(0, errorString);
        }
    }
}
