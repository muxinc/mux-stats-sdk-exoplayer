package com.mux.stats.sdk.muxstats;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import androidx.test.InstrumentationRegistry;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.AssetDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.mux.stats.sdk.R;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class MuxStatsLateInitInstrumentationTest {

    public static final String TAG = "MuxStatsLateInitInstrumentationTest";

    @Rule
    public ActivityTestRule<SimplePlayerTestActivity> activityTestRule;

//    private FetchingIdlingResource fetchingResource = new FetchingIdlingResource();

    SimplePlayerTestActivity testActivity;
    private PlayerView playerView;
    private SimpleExoPlayer player;
    private MuxStatsExoPlayer muxStats;


    @Before
    public void init(){
        activityTestRule = new ActivityTestRule<SimplePlayerTestActivity>(
                SimplePlayerTestActivity.class,
                true,
                false);
        activityTestRule.launchActivity(null);
        testActivity = activityTestRule.getActivity();
//        IdlingRegistry.getInstance().register(fetchingResource);
    }

    @Test
    public void testLateInit() {
        player = testActivity.player;

//        player.retry();
        Handler lHandler = new Handler(testActivity.getMainLooper());

        lHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                preparePlayback();
            }
        }, 0);

        lHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                initMuxSats();
            }
        }, 4000);

        onView(withId(R.id.player_view))
                .perform(wait8seconds());

    }

    private void preparePlayback() {
//        Uri testUri = Uri.parse("http://vod.leasewebcdn.com/bbb.flv?ri=1024&rs=150&start=0");
        Uri testUri = Uri.parse("assets:///sample-1280x720.mkv");

//        MediaSource testMediaSource = new ProgressiveMediaSource.Factory(
//                new DefaultDataSourceFactory(testActivity, "Test"))
//                .createMediaSource(testUri);
        DataSpec dataSpec = new DataSpec(testUri);
        final AssetDataSource assetDataSource = new AssetDataSource(testActivity);
        try {
            assetDataSource.open(dataSpec);
        } catch (AssetDataSource.AssetDataSourceException e) {
            e.printStackTrace();
        }

        DataSource.Factory factory = new DataSource.Factory() {
            @Override
            public DataSource createDataSource() {
                //return rawResourceDataSource;
                return assetDataSource;
            }
        };

        MediaSource testMediaSource = new ExtractorMediaSource(assetDataSource.getUri(),
                factory, new DefaultExtractorsFactory(), null, null);

        player.setPlayWhenReady(true);
        player.prepare(testMediaSource, false, false);
    }

    private void initMuxSats() {
        // Mux details
        CustomerPlayerData customerPlayerData = new CustomerPlayerData();
        customerPlayerData.setEnvironmentKey("YOUR_ENVIRONMENT_KEY");
        CustomerVideoData customerVideoData = new CustomerVideoData();
        customerVideoData.setVideoTitle("Test video");
        MuxStatsExoPlayer muxStats = new MuxStatsExoPlayer(
                testActivity, player, "demo-player", customerPlayerData, customerVideoData);
        Point size = new Point();
        testActivity.getWindowManager().getDefaultDisplay().getSize(size);
        muxStats.setScreenSize(size.x, size.y);
        muxStats.setPlayerView(playerView);
        muxStats.enableMuxCoreDebug(true, false);
    }


    static ViewAction wait8seconds() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isEnabled(); // No constraints, isEnabled and isClickable are checked
            }

            @Override
            public String getDescription() {
                return "Click a view with no constraints.";
            }

            @Override
            public void perform(UiController uiController, View view) {
                uiController.loopMainThreadForAtLeast(8000);
            }
        };
    }
}