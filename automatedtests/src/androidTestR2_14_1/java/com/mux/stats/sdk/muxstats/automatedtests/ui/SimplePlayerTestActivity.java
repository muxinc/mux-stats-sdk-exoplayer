package com.mux.stats.sdk.muxstats.automatedtests.ui;


import android.app.Notification;
import android.net.Uri;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaItem.AdsConfiguration;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.mux.stats.sdk.muxstats.automatedtests.R;


public class SimplePlayerTestActivity extends SimplePlayerBaseActivity implements PlaybackPreparer {

  MediaSourceFactory mediaSourceFactory;

  public void initExoPlayer() {
    // Hopfully this will not channge the track selection set programmatically
    ExoTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(
        AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS * 10,
        AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS * 10,
        AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
        AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION
    );
    DefaultTrackSelector.ParametersBuilder builder =
        new DefaultTrackSelector.ParametersBuilder(/* context= */ this);
    DefaultTrackSelector.Parameters trackSelectorParameters = builder
        .build();

    mediaSourceFactory =
        new DefaultMediaSourceFactory(buildDataSourceFactory())
            .setAdsLoaderProvider(this::getAdsLoader)
            .setAdViewProvider(playerView);

    trackSelector = new DefaultTrackSelector(/* context= */ this, trackSelectionFactory);
    trackSelector.setParameters(trackSelectorParameters);
    RenderersFactory renderersFactory = new DefaultRenderersFactory(/* context= */ this);
    player =
        new SimpleExoPlayer.Builder(/* context= */ this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build();
    playerView.setPlaybackPreparer(this);
  }

  @Override
  public void preparePlayback() {
    player.prepare();
  }

  // This is for background playback, set appropriate notification and etc
  public void initAudioSession() {
    notificationManager = PlayerNotificationManager.createWithNotificationChannel(
        getApplicationContext(),
        PLAYBACK_CHANNEL_ID,
        R.string.channel_name,
        R.string.channel_description,
        PLAYBACK_NOTIFICATION_ID,
        new MDAdapter(),
        new CustomNotificationListener()
    );
    notificationManager.setUseNavigationActions(false);
    notificationManager.setUseStopAction(true);
    notificationManager.setPlayer(player);

    mediaSessionCompat = new MediaSessionCompat(this, "hello_world_media");
    notificationManager.setMediaSessionToken(mediaSessionCompat.getSessionToken());
    mediaSessionConnector = new MediaSessionConnector(mediaSessionCompat);
    mediaSessionConnector.setPlayer(player);
  }

  public DataSource.Factory buildDataSourceFactory() {
    return new DefaultDataSourceFactory(this, "Android-automated_tests");
  }

  private MediaSource createMediaSource(Uri uri, String extension) {
    MediaItem lMEdiaItem = createMediaItem(uri);
    @C.ContentType int type = Util.inferContentType(uri, extension);
    DataSource.Factory dataSourceFactory = buildDataSourceFactory();
    switch (type) {
      case C.TYPE_DASH:
        return new DashMediaSource.Factory(dataSourceFactory)
            .createMediaSource(lMEdiaItem);
      case C.TYPE_SS:
        return new SsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(lMEdiaItem);
      case C.TYPE_HLS:
        return new HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(lMEdiaItem);
      case C.TYPE_OTHER:
        return new ProgressiveMediaSource.Factory(buildDataSourceFactory())
            .createMediaSource(lMEdiaItem);
      default:
        throw new IllegalStateException("Unsupported type: " + type);
    }
  }

  public MediaSource createAdsMediaSource(MediaSource aMediaSource, Uri adTagUri) {
    return new AdsMediaSource(
        aMediaSource,
        new DataSpec(loadedAdTagUri),
        this, // I hope this is ok
        mediaSourceFactory,
        getAdsLoader(aMediaSource.getMediaItem().playbackProperties.adsConfiguration),
        playerView);
  }

  public MediaSource buildMediaSource(Uri uri, @Nullable String overrideExtension) {
    return createMediaSource(uri, overrideExtension);
  }

  private MediaItem createMediaItem(
      Uri uri) {
    MediaItem.Builder lBuilder =
        new MediaItem.Builder()
            .setUri(uri);
    return lBuilder.build();
  }


  private AdsLoader getAdsLoader(AdsConfiguration adsConfiguration) {
    if (adsConfiguration != null && adsConfiguration.adTagUri != null) {
      Uri adTagUri = adsConfiguration.adTagUri;
      if (!adTagUri.equals(loadedAdTagUri)) {
        releaseAdsLoader();
        loadedAdTagUri = adTagUri;
      }
    }
    // The ads loader is reused for multiple playbacks, so that ad playback can resume.
    if (adsLoader == null) {
      adsLoader = new ImaAdsLoader.Builder(/* context= */ this)
          .setAdErrorListener(muxStats.getAdsImaSdkListener())
          .setAdEventListener(muxStats.getAdsImaSdkListener())
          .build();
    }
    adsLoader.setPlayer(player);
    return adsLoader;
  }

  private void releaseAdsLoader() {
    if (adsLoader != null) {
      adsLoader.release();
      adsLoader = null;
      loadedAdTagUri = null;
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
    player.setMediaSource(testMediaSource);
    player.seekTo(playbackStartPosition);
    player.prepare();
  }

  class CustomNotificationListener implements PlayerNotificationManager.NotificationListener {

    @Override
    public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
      // TODO implement this
      Log.e(TAG, "onNotificationCancelled");
    }

    @Override
    public void onNotificationPosted(int notificationId, Notification notification,
        boolean ongoing) {
      // TODO implement this
      Log.e(TAG, "onNotificationPosted");
    }
  }
}
