package com.mux.stats.sdk.muxstats.automatedtests.ui;


import android.net.Uri;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.Nullable;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.mux.stats.sdk.muxstats.automatedtests.R;
import com.mux.stats.sdk.muxstats.ima.MuxImaAdsLoader;
import com.mux.stats.sdk.muxstats.ima.MuxImaAdsLoader.Builder;
import java.lang.reflect.Constructor;
import com.mux.stats.sdk.muxstats.SimplePlayerBaseActivity;

public class SimplePlayerTestActivity extends SimplePlayerBaseActivity
    implements Player.EventListener {

  public void initExoPlayer() {
    // Hopfully this will not channge the track selection set programmatically
    TrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(
        AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS * 10,
        AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS * 10,
        AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
        AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION
    );
    DefaultTrackSelector.Parameters trackSelectorParameters
        = new DefaultTrackSelector.ParametersBuilder().build();
    trackSelector = new DefaultTrackSelector(trackSelectionFactory);
    trackSelector.setParameters(trackSelectorParameters);
    RenderersFactory renderersFactory = new DefaultRenderersFactory(/* context= */ this);
    player = ExoPlayerFactory.newSimpleInstance(this, renderersFactory, trackSelector);
    player.addListener(this);
  }

  public void initAudioSession() {
    notificationManager = PlayerNotificationManager.createWithNotificationChannel(
        getApplicationContext(),
        PLAYBACK_CHANNEL_ID,
        R.string.channel_name,
        PLAYBACK_NOTIFICATION_ID,
        new MDAdapter()
    );

    notificationManager.setUseNavigationActions(false);
    notificationManager.setPlayer(player);
    mediaSessionCompat = new MediaSessionCompat(this, "hello_world_media");
    notificationManager.setMediaSessionToken(mediaSessionCompat.getSessionToken());
    mediaSessionConnector = new MediaSessionConnector(mediaSessionCompat);
  }

  public DataSource.Factory buildDataSourceFactory() {
    return new DefaultDataSourceFactory(this, "Android-automated_tests");
  }

  @SuppressWarnings("unchecked")
  public MediaSource buildMediaSource(Uri uri, @Nullable String overrideExtension) {
    @C.ContentType int type = Util.inferContentType(uri, overrideExtension);
    DataSource.Factory dataSourceFactory = buildDataSourceFactory();
    switch (type) {
      case C.TYPE_DASH:
        return new DashMediaSource.Factory(dataSourceFactory)
            .createMediaSource(uri);
      case C.TYPE_SS:
        return new SsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(uri);
      case C.TYPE_HLS:
        return new HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(uri);
      case C.TYPE_OTHER:
        return new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
      default: {
        throw new IllegalStateException("Unsupported type: " + type);
      }
    }
  }

  /**
   * Returns an ads media source, reusing the ads loader if one exists.
   */
  public @Nullable
  MediaSource createAdsMediaSource(MediaSource mediaSource, Uri adTagUri) {
    try {
      AdsMediaSource.MediaSourceFactory adMediaSourceFactory =
          new AdsMediaSource.MediaSourceFactory() {
            @Override
            public MediaSource createMediaSource(Uri uri) {
              return SimplePlayerTestActivity.this.buildMediaSource(uri, null);
            }

            @Override
            public int[] getSupportedTypes() {
              return new int[]{C.TYPE_DASH, C.TYPE_SS, C.TYPE_HLS, C.TYPE_OTHER};
            }
          };
      MuxImaAdsLoader.Builder adsBuilder = new Builder(this)
          .setAdTagUri(adTagUri)
          .addAdEventListener(muxStats.getAdsImaSdkListener())
          .addAdErrorListener(muxStats.getAdsImaSdkListener());
      for (AdEventListener l : additionalAdEventListeners) {
        adsBuilder.addAdEventListener(l);
      }
      for (AdErrorListener l : additionalAdErrorListeners) {
        adsBuilder.addAdErrorListener(l);
      }
      adsLoader = adsBuilder.build();
      adsLoader.setPlayer(player);
      return new AdsMediaSource(mediaSource, adMediaSourceFactory, adsLoader, playerView);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void startPlayback() {
    Uri testUri = Uri.parse(urlToPlay);
    testMediaSource = buildMediaSource(testUri, null);
    if (loadedAdTagUri != null) {
      testMediaSource = createAdsMediaSource(testMediaSource, loadedAdTagUri);
    }

    player.setPlayWhenReady(playWhenReady);
    player.seekTo(playbackStartPosition);
    ((SimpleExoPlayer)player).prepare(testMediaSource, false, true);
  }

  //////////////////////////////////////////////////////////////////////
  ////// Player.EventListener //////////////////////////////////////////

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
      case Player.STATE_IDLE:
        signalPlaybackStopped();
        break;
    }
  }

  @Override
  public void onRepeatModeChanged(int repeatMode) {
    activityLock.lock();
    activityInitialized.signalAll();
    activityLock.unlock();
  }
}
