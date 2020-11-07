package com.mux.stats.sdk.muxstats.automatedtests.ui;


import android.net.Uri;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.offline.FilteringManifestParser;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifestParser;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.mux.stats.sdk.muxstats.automatedtests.R;

import java.lang.reflect.Constructor;

public class SimplePlayerTestActivity extends SimplePlayerBaseActivity {

    public void initExoPlayer() {
        TrackSelection.Factory trackSelectionFactory = new RandomTrackSelection.Factory();;
        DefaultTrackSelector.Parameters trackSelectorParameters
                = new DefaultTrackSelector.ParametersBuilder().build();
        trackSelector = new DefaultTrackSelector(trackSelectionFactory);
        trackSelector.setParameters(trackSelectorParameters);
        RenderersFactory renderersFactory = new DefaultRenderersFactory(/* context= */ this);
        player = ExoPlayerFactory.newSimpleInstance(this, renderersFactory, trackSelector);
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
        muxStats.setStreamType(type);
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

    /** Returns an ads media source, reusing the ads loader if one exists. */
    public  @Nullable
    MediaSource createAdsMediaSource(MediaSource mediaSource, Uri adTagUri) {
        // Load the extension source using reflection so the demo app doesn't have to depend on it.
        // The ads loader is reused for multiple playbacks, so that ad playback can resume.
        try {
            Class<?> loaderClass = Class.forName("com.google.android.exoplayer2.ext.ima.ImaAdsLoader");
            if (adsLoader == null) {
                // Full class names used so the LINT.IfChange rule triggers should any of the classes move.
                // LINT.IfChange
                Constructor<? extends AdsLoader> loaderConstructor =
                        loaderClass
                                .asSubclass(AdsLoader.class)
                                .getConstructor(android.content.Context.class, android.net.Uri.class);
                // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
                adsLoader = loaderConstructor.newInstance(this, adTagUri);
            }
            adsLoader.setPlayer(player);
            AdsMediaSource.MediaSourceFactory adMediaSourceFactory =
                    new AdsMediaSource.MediaSourceFactory() {
                        @Override
                        public MediaSource createMediaSource(Uri uri) {
                            return SimplePlayerTestActivity.this.buildMediaSource(uri, null);
                        }

                        @Override
                        public int[] getSupportedTypes() {
                            return new int[] {C.TYPE_DASH, C.TYPE_SS, C.TYPE_HLS, C.TYPE_OTHER};
                        }
                    };

            // Because of how this is loaded via reflection, we know that this will be
            // a ImaAdsLoader, so cast it over so that we can get a reference to the
            // real IMA AdsLoader instance.
            muxStats.monitorImaAdsLoader(((ImaAdsLoader) adsLoader).getAdsLoader());
            return new AdsMediaSource(mediaSource, adMediaSourceFactory, adsLoader, playerView);
        } catch (ClassNotFoundException e) {
            // IMA extension not loaded.
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
