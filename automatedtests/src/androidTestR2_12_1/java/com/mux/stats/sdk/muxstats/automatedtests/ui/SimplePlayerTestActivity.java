package com.mux.stats.sdk.muxstats.automatedtests.ui;


import android.app.Notification;
import android.net.Uri;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.mux.stats.sdk.muxstats.automatedtests.R;

public class SimplePlayerTestActivity extends SimplePlayerBaseActivity {

    public void initExoPlayer() {
        RenderersFactory renderersFactory = new DefaultRenderersFactory(/* context= */ this);
        DefaultTrackSelector.ParametersBuilder builder =
                new DefaultTrackSelector.ParametersBuilder(/* context= */ this);
        DefaultTrackSelector.Parameters trackSelectorParameters = builder.build();
        MediaSourceFactory mediaSourceFactory =
                new DefaultMediaSourceFactory( buildDataSourceFactory() )
                        .setAdsLoaderProvider(this::getAdsLoader)
                        .setAdViewProvider(playerView);
        trackSelector = new DefaultTrackSelector(/* context= */ this);
        trackSelector.setParameters(trackSelectorParameters);
        player =
                new SimpleExoPlayer.Builder(/* context= */ this, renderersFactory)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .setTrackSelector(trackSelector)
                        .build();
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

    private MediaSource createMediaSource(MediaItem aAdMediaItem, Uri uri, String extension) {
        @C.ContentType int type = Util.inferContentType(uri, extension);
        DataSource.Factory dataSourceFactory = buildDataSourceFactory();
        switch (type) {
            case C.TYPE_DASH:
                return new DashMediaSource.Factory (dataSourceFactory)
                        .createMediaSource(aAdMediaItem);
            case C.TYPE_SS:
                return new SsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(aAdMediaItem);
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(aAdMediaItem);
            case C.TYPE_OTHER:
                return new ProgressiveMediaSource.Factory( buildDataSourceFactory() )
                        .createMediaSource(aAdMediaItem);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    private MediaSource createLeafMediaSource(
            Uri uri, String extension, DrmSessionManager drmSessionManager) {
        MediaItem.Builder lBuilder =
                new MediaItem.Builder()
                        .setUri(uri)
                        .setMimeType(extension);
        MediaItem lMediaItem = lBuilder.build();
        return createMediaSource( lMediaItem, uri, extension );
    }

    @Override
    public MediaSource buildMediaSource(Uri uri, @Nullable String overrideExtension) {
        return createLeafMediaSource(uri, overrideExtension, DrmSessionManager.getDummyDrmSessionManager());
    }


    /** Returns an ads media source, reusing the ads loader if one exists. */
    public MediaSource createAdsMediaSource(Uri uri, Uri adTagUri) {
        MediaItem.Builder lBuilder =
                new MediaItem.Builder()
                        .setUri(uri)
                        .setAdTagUri(adTagUri);
        MediaItem lAdMediaItem = lBuilder.build();
        return createMediaSource( lAdMediaItem, uri, null );
    }

    private AdsLoader getAdsLoader(Uri adTagUri) {
        if (!adTagUri.equals(loadedAdTagUri)) {
            releaseAdsLoader();
            loadedAdTagUri = adTagUri;
        }
        // The ads loader is reused for multiple playbacks, so that ad playback can resume.
        if (adsLoader == null) {
            adsLoader = new ImaAdsLoader.Builder(/* context= */ this).build();
        }
        adsLoader.setPlayer(player);
        return adsLoader;
    }

    private void releaseAdsLoader() {
        if (adsLoader != null) {
            adsLoader.release();
            adsLoader = null;
            loadedAdTagUri = null;
            playerView.getOverlayFrameLayout().removeAllViews();
        }
    }


    class CustomNotificationListener implements PlayerNotificationManager.NotificationListener {

        @Override
        public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
            // TODO implement this
            Log.e(TAG, "onNotificationCancelled");
        }

        @Override
        public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
            // TODO implement this
            Log.e(TAG, "onNotificationPosted");
        }
    }
}
