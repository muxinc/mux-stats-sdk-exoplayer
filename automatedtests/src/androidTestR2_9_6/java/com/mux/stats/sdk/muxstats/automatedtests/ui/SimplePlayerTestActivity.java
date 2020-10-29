package com.mux.stats.sdk.muxstats.automatedtests.ui;


import android.support.v4.media.session.MediaSessionCompat;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.mux.stats.sdk.muxstats.automatedtests.R;

public class SimplePlayerTestActivity extends SimplePlayerBaseActivity {

    public void initExoPlayer() {
        RenderersFactory renderersFactory = new DefaultRenderersFactory(/* context= */ this);
        TrackSelection.Factory trackSelectionFactory = new RandomTrackSelection.Factory();
        TrackSelector trackSelector = new DefaultTrackSelector(trackSelectionFactory);
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
}
