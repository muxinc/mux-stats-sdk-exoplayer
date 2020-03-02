package com.mux.stats.sdk.muxstats;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.net.Uri;
import android.os.Handler;
import android.view.View;

import androidx.test.InstrumentationRegistry;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.runner.AndroidJUnit4;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.mux.stats.sdk.R;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;

import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class MuxStatsLateInitInstrumentationTest {



    Context context;

    private PlayerView playerView;
    private SimpleExoPlayer player;
    private MuxStatsExoPlayer muxStats;

    @Test
    public void testAppContext() {

        SimplePlayerTestActivity activity = Robolectric.setupActivity(SimplePlayerTestActivity.class);

        context = InstrumentationRegistry.getTargetContext();
        ViewInteraction interaction = onView(withId(R.id.player_view));
        interaction.perform(click());

        SetupExoPlayerAction setupAction = new SetupExoPlayerAction();
        onView(withId(R.id.player_view)).perform(setupAction);
//        assertEquals("com.example.hotmicsyncproto", appContext.getPackageName());
    }

    public class SetupExoPlayerAction implements ViewAction {

        @Override
        public Matcher<View> getConstraints(){
            return isAssignableFrom(PlayerView.class);
        }


        @Override
        public String getDescription(){
            return "Setup ExoPlayer view and MuxStats";
        }

        @Override
        public void perform(UiController uiController, View view){
            playerView = (PlayerView) view;

            RenderersFactory renderersFactory = buildRenderersFactory(false);
            TrackSelection.Factory trackSelectionFactory = new RandomTrackSelection.Factory();
            TrackSelector trackSelector = new DefaultTrackSelector(trackSelectionFactory);

            player =
                    ExoPlayerFactory.newSimpleInstance(
                            context,
                            renderersFactory,
                            trackSelector);

            playerView.setPlayer(player);
            Uri testUri = Uri.parse("https://storage.googleapis.com/exoplayer-test-media-1/mkv/android-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv");
            MediaSource testMediaSource = new ProgressiveMediaSource.Factory(buildDataSourceFactory()).createMediaSource(testUri);

            CustomerPlayerData customerPlayerData = new CustomerPlayerData();
            customerPlayerData.setEnvironmentKey("YOUR_ENVIRONMENT_KEY");
            CustomerVideoData customerVideoData = new CustomerVideoData();
            customerVideoData.setVideoTitle("Test video");
            muxStats = new MuxStatsExoPlayer(
                    context,
                    player,
                    "demo-player",
                    customerPlayerData,
                    customerVideoData);
            Point size = new Point();
            context.getWindowManager().getDefaultDisplay().getSize(size);
            muxStats.setScreenSize(size.x, size.y);
            muxStats.setPlayerView(playerView);
            muxStats.enableMuxCoreDebug(true, false);

        }

    }

    private RenderersFactory buildRenderersFactory(boolean preferExtensionRenderer) {
        return new DefaultRenderersFactory(InstrumentationRegistry.getTargetContext());
    }

    private DataSource.Factory buildDataSourceFactory() {
        return new DefaultDataSourceFactory(this, "Test");
    }
}