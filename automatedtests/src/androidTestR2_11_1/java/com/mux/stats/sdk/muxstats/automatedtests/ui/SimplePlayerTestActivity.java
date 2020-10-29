package com.mux.stats.sdk.muxstats.automatedtests.ui;


import android.app.Notification;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

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
