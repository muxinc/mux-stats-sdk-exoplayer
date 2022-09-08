package com.mux.stats.sdk.muxstats;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.ui.PlayerView;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.muxstats.automatedtests.BuildConfig;
import com.mux.stats.sdk.muxstats.automatedtests.R;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.MockNetworkRequest;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class SimplePlayerBaseActivity extends AppCompatActivity {

  public static final String TAG = "SimplePlayerActivity";

  protected static final String PLAYBACK_CHANNEL_ID = "playback_channel";
  protected static final int PLAYBACK_NOTIFICATION_ID = 1;
  protected static final String ARG_URI = "uri_string";
  protected static final String ARG_TITLE = "title";
  protected static final String ARG_START_POSITION = "start_position";

  public String videoTitle = "Test Video";
  public String urlToPlay;
  public PlayerView playerView;
  public ExoPlayer player;
  public DefaultTrackSelector trackSelector;
  public MediaSource testMediaSource;
  public MuxStatsExoPlayer muxStats;
  public AdsLoader adsLoader;
  public Uri loadedAdTagUri;
  public boolean playWhenReady = true;
  public MockNetworkRequest mockNetwork;
  public AtomicBoolean onResumedCalled = new AtomicBoolean(false);
  public PlayerNotificationManager notificationManager;
  public MediaSessionCompat mediaSessionCompat;
  public MediaSessionConnector mediaSessionConnector;
  public long playbackStartPosition = 0;

  public Lock activityLock = new ReentrantLock();
  public Condition playbackEnded = activityLock.newCondition();
  public Condition playbackStopped = activityLock.newCondition();
  public Condition seekEnded = activityLock.newCondition();
  public Condition playbackStarted = activityLock.newCondition();
  public Condition playbackBuffering = activityLock.newCondition();
  public Condition activityClosed = activityLock.newCondition();
  public Condition activityInitialized = activityLock.newCondition();
  public ArrayList<String> addAllowedHeaders = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Enter fullscreen
    hideSystemUI();
    setContentView(R.layout.activity_simple_player_test);
    disableUserActions();

    playerView = findViewById(R.id.player_view);

    initExoPlayer();
    playerView.setPlayer(player);

    // Do not hide controlls
    playerView.setControllerShowTimeoutMs(0);
    playerView.setControllerHideOnTouch(false);

    // Setup notification and media session.
    initAudioSession();
  }

  public void allowHeaderToBeSentToBackend(String headerName) {
    addAllowedHeaders.add(headerName);
  }

  @Override
  protected void onResume() {
    super.onResume();
    onResumedCalled.set(true);
  }

  @Override
  public void onStop() {
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    signalActivityClosed();
    if (muxStats != null) {
      muxStats.release();
    }
    releaseExoPlayer();
  }

  public abstract void initExoPlayer();

  public abstract void initAudioSession();

  public abstract void startPlayback();

  public void setPlayWhenReady(boolean playWhenReady) {
    this.playWhenReady = playWhenReady;
  }

  public void setVideoTitle(String title) {
    videoTitle = title;
  }

  public void setAdTag(String tag) {
    loadedAdTagUri = Uri.parse(tag);
  }

  public void setUrlToPlay(String url) {
    urlToPlay = url;
  }

  public DefaultTrackSelector getTrackSelector() {
    return trackSelector;
  }

  public void setPlaybackStartPosition(long position) {
    playbackStartPosition = position;
  }

  public void releaseExoPlayer() {
    player.release();
    player = null;
  }

  public void hideSystemUI() {
    // Enables regular immersive mode.
    // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
    // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
      View decorView = getWindow().getDecorView();
      decorView.setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              // Set the content to appear under the system bars so that the
              // content doesn't resize when the system bars hide and show.
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              // Hide the nav bar and status bar
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
  }

  // Shows the system bars by removing all the flags
  // except for the ones that make the content appear under the system bars.
  public void showSystemUI() {
    View decorView = getWindow().getDecorView();
    decorView.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
  }

  public MuxStatsExoPlayer getMuxStats() {
    return muxStats;
  }

  public void initMuxSats() {
    // Mux details
    CustomerPlayerData customerPlayerData = new CustomerPlayerData();
    if (BuildConfig.SHOULD_REPORT_INSTRUMENTATION_TEST_EVENTS_TO_SERVER) {
      customerPlayerData.setEnvironmentKey(BuildConfig.INSTRUMENTATION_TEST_ENVIRONMENT_KEY);
    } else {
      customerPlayerData.setEnvironmentKey("YOUR_ENVIRONMENT_KEY");
    }
    CustomerVideoData customerVideoData = new CustomerVideoData();
    customerVideoData.setVideoTitle(videoTitle);
    mockNetwork = new MockNetworkRequest();
    CustomerData customerData = new CustomerData(customerPlayerData, customerVideoData, null);
    muxStats = new MuxStatsExoPlayer(
        this, (ExoPlayer) player, playerView, "demo-player", customerData, null, mockNetwork);
    Point size = new Point();
    // These need to be set in order to calculate if the app is in the full screen mode.
    getWindowManager().getDefaultDisplay().getSize(size);
    muxStats.setScreenSize(size.x, size.y);
    muxStats.setPlayerView(playerView);
    muxStats.enableMuxCoreDebug(true, false);
    for (String headerName : addAllowedHeaders) {
      MuxStatsHelper.allowHeaderToBeSentToBackend(muxStats, headerName);
    }
  }

  public MediaSource getTestMediaSource() {
    return testMediaSource;
  }

  public PlayerView getPlayerView() {
    return playerView;
  }

  public MockNetworkRequest getMockNetwork() {
    return mockNetwork;
  }

  public boolean waitForPlaybackToStop(long timeoutInMs) {
    try {
      activityLock.lock();
      return playbackStopped.await(timeoutInMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
    } finally {
      activityLock.unlock();
    }
  }

//  public boolean waitForSeekEnd(long timeoutInMs) {
//    try {
//      activityLock.lock();
//      return seekEnded.await(timeoutInMs, TimeUnit.MILLISECONDS);
//    } catch (InterruptedException e) {
//      e.printStackTrace();
//      return false;
//    } finally {
//      activityLock.unlock();
//    }
//  }

  public boolean waitForPlaybackToFinish(long timeoutInMs) {
    try {
      activityLock.lock();
      return playbackEnded.await(timeoutInMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
    } finally {
      activityLock.unlock();
    }
  }

  public void waitForActivityToInitialize() {
    if (!onResumedCalled.get()) {
      try {
        activityLock.lock();
        activityInitialized.await();
        activityLock.unlock();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public boolean waitForPlaybackToStart(long timeoutInMs) {
    try {
      activityLock.lock();
      return playbackStarted.await(timeoutInMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
    } finally {
      activityLock.unlock();
    }
  }

  public void waitForPlaybackToStartBuffering() {
    if (!muxStats.isPaused()) {
      try {
        activityLock.lock();
        playbackBuffering.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
      } finally {
        activityLock.unlock();
      }
    }
  }

  public void waitForActivityToClose() {
    try {
      activityLock.lock();
      activityClosed.await();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      activityLock.unlock();
    }
  }

  public void signalPlaybackStarted() {
    activityLock.lock();
    playbackStarted.signalAll();
    activityLock.unlock();
  }

  public void signalPlaybackStopped() {
    activityLock.lock();
    playbackStopped.signalAll();
    activityLock.unlock();
  }

  public void signalSeekEnded() {
    activityLock.lock();
    seekEnded.signalAll();
    activityLock.unlock();
  }

  public void signalPlaybackBuffering() {
    activityLock.lock();
    playbackBuffering.signalAll();
    activityLock.unlock();
  }

  public void signalPlaybackEnded() {
    activityLock.lock();
    playbackEnded.signalAll();
    activityLock.unlock();
  }

  public void signalActivityClosed() {
    activityLock.lock();
    activityClosed.signalAll();
    activityLock.unlock();
  }

  private void disableUserActions() {
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
  }

  private void enableUserActions() {
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
  }

  public class MDAdapter implements PlayerNotificationManager.MediaDescriptionAdapter {

    @Override
    public String getCurrentContentTitle(Player player) {
      return "Tittle";
    }

    @Nullable
    @Override
    public PendingIntent createCurrentContentIntent(Player player) {
      return PendingIntent.getActivity(
          getApplicationContext(),
          0,
          new Intent(getApplicationContext(), SimplePlayerBaseActivity.class),
          PendingIntent.FLAG_UPDATE_CURRENT
      );
    }

    @Nullable
    @Override
    public String getCurrentContentText(Player player) {
      return "Automated test playback";
    }

    @Nullable
    @Override
    public Bitmap getCurrentLargeIcon(Player player,
        PlayerNotificationManager.BitmapCallback callback) {
      return getBitmapFromVectorDrawable(R.drawable.ic_launcher_foreground);
    }

    @MainThread
    private Bitmap getBitmapFromVectorDrawable(int drawableId) {
      Drawable drawable = ContextCompat.getDrawable(getApplicationContext(), drawableId);
      DrawableCompat.wrap(drawable).mutate();
      Bitmap bmp = Bitmap.createBitmap(
          drawable.getIntrinsicWidth(),
          drawable.getIntrinsicHeight(),
          Bitmap.Config.ARGB_8888);
      Canvas cnvs = new Canvas(bmp);
      drawable.setBounds(0, 0, cnvs.getWidth(), cnvs.getHeight());
      drawable.draw(cnvs);
      return bmp;
    }
  }
}