package com.mux.stats.sdk.muxstats;

import static android.os.SystemClock.elapsedRealtime;

import android.app.usage.UsageEvents.Event;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaFormat;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import androidx.annotation.Nullable;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.VideoComponent;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.HlsManifest;
import com.google.android.exoplayer2.source.hls.HlsTrackMetadataEntry;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.mux.stats.sdk.core.MuxSdkViewOrientation;
import com.mux.stats.sdk.core.events.EventBus;
import com.mux.stats.sdk.core.events.IEvent;
import com.mux.stats.sdk.core.events.InternalErrorEvent;
import com.mux.stats.sdk.core.events.playback.EndedEvent;
import com.mux.stats.sdk.core.events.playback.PauseEvent;
import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlaybackEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.RebufferEndEvent;
import com.mux.stats.sdk.core.events.playback.RebufferStartEvent;
import com.mux.stats.sdk.core.events.playback.RenditionChangeEvent;
import com.mux.stats.sdk.core.events.playback.RequestBandwidthEvent;
import com.mux.stats.sdk.core.events.playback.RequestCanceled;
import com.mux.stats.sdk.core.events.playback.RequestCompleted;
import com.mux.stats.sdk.core.events.playback.RequestFailed;
import com.mux.stats.sdk.core.events.playback.SeekedEvent;
import com.mux.stats.sdk.core.events.playback.SeekingEvent;
import com.mux.stats.sdk.core.events.playback.TimeUpdateEvent;
import com.mux.stats.sdk.core.model.BandwidthMetricData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.core.model.CustomerViewData;
import com.mux.stats.sdk.core.util.MuxLogger;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MuxBaseExoPlayer extends EventBus implements IplayerListener {

  protected static final String TAG = "MuxStatsListener";
  // Error codes start at -1 as ExoPlaybackException codes start at 0 and go up.
  protected static final int ERROR_UNKNOWN = -1;
  protected static final int ERROR_DRM = -2;
  protected static final int ERROR_IO = -3;

  protected static final int NUMBER_OF_FRAMES_THAT_ARE_CONSIDERED_PLAYBACK = 2;

  protected String mimeType;
  protected Integer sourceWidth;
  protected Integer sourceHeight;
  protected Integer sourceAdvertisedBitrate;
  protected Float sourceAdvertisedFramerate;
  protected Long sourceDuration;
  protected ExoPlayerHandler playerHandler;
  protected FrameRenderedListener frameRenderedListener;
  protected Timer updatePlayheadPositionTimer;

  protected WeakReference<ExoPlayer> player;
  protected WeakReference<View> playerView;
  protected WeakReference<Context> contextRef;
  protected AdsImaSDKListener adsImaSdkListener;
  protected Lock muxStatsLock = new ReentrantLock();
  protected Condition newMediaSegmentStarted = muxStatsLock.newCondition();

  protected int streamType = -1;

  public enum PlayerState {
    BUFFERING, REBUFFERING, SEEKING, SEEKED, ERROR, PAUSED, PLAY, PLAYING, PLAYING_ADS,
    FINISHED_PLAYING_ADS, INIT, ENDED
  }

  protected PlayerState state;
  protected MuxStats muxStats;
  boolean seekingInProgress;
  int numberOfFramesRenderedSinceSeekingStarted;
  boolean playItemHaveVideoTrack;


  MuxBaseExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData, CustomerVideoData customerVideoData,
      CustomerViewData customerViewData, boolean sentryEnabled,
      InetworkRequest networkRequest) {
    super();
    this.player = new WeakReference<>(player);
    this.contextRef = new WeakReference<>(ctx);
    state = PlayerState.INIT;
    MuxStats.setHostDevice(new MuxDevice(ctx));
    MuxStats.setHostNetworkApi(networkRequest);
    muxStats = new MuxStats(this, playerName, customerPlayerData, customerVideoData,
        customerViewData, sentryEnabled);
    addListener(muxStats);
    playerHandler = new ExoPlayerHandler(player.getApplicationLooper(), this);
    frameRenderedListener = new FrameRenderedListener(playerHandler);
    playItemHaveVideoTrack = false;
    setPlaybackHeadUpdateInterval();
    try {
      adsImaSdkListener = new AdsImaSDKListener(this);
    } catch (NoClassDefFoundError Err) {
      // The ad modules are not included here, so we silently swallow the
      // exception as the application can't be running ads anyway.
    }
  }

  /**
   * Get the instance of the IMA SDK Listener for tracking ads running through Google's IMA SDK
   * within your application.
   *
   * @return the IMA SDK Listener
   * @throws
   * @deprecated This method is no longer the preferred method to track Ad performance with Google's
   * IMA SDK.
   * <p> Use {@link MuxBaseExoPlayer#monitorImaAdsLoader(AdsLoader)} instead.
   */
  @Deprecated
  public AdsImaSDKListener getIMASdkListener() {
    try {
      // Let's just check one of them
      Class.forName("com.google.ads.interactivemedia.v3.api.Ad");
      Class.forName("com.google.ads.interactivemedia.v3.api.AdErrorEvent");
      Class.forName("com.google.ads.interactivemedia.v3.api.AdEvent");
      return new AdsImaSDKListener(this);
    } catch (ClassNotFoundException cnfe) {
      throw new IllegalStateException("IMA SDK Modules not found");
    }
  }

  /**
   * Monitor an instance of Google IMA SDK's AdsLoader
   *
   * @param adsLoader For ExoPlayer 2.12 AdsLoader is initialized only when the add is requested,
   *                  this makes this method impossible to use.
   */
  @SuppressWarnings("unused")
  public void monitorImaAdsLoader(AdsLoader adsLoader) {
    if (adsLoader == null) {
      Log.e(TAG, "Null AdsLoader provided to monitorImaAdsLoader");
      return;
    }
    try {
      // TODO: these may not be necessary, but doing it for the sake of it
      Class.forName("com.google.ads.interactivemedia.v3.api.AdsLoader");
      Class.forName("com.google.ads.interactivemedia.v3.api.AdsManager");
      Class.forName("com.google.ads.interactivemedia.v3.api.AdErrorEvent");
      Class.forName("com.google.ads.interactivemedia.v3.api.AdEvent");
      Class.forName("com.google.ads.interactivemedia.v3.api.Ad");
      final MuxBaseExoPlayer baseExoPlayer = this;
      adsLoader.addAdsLoadedListener(new AdsLoader.AdsLoadedListener() {
        @Override
        public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
          // TODO: Add in the adresponse stuff when we can

          // Set up the ad events that we want to use
          AdsManager adsManager = adsManagerLoadedEvent.getAdsManager();

          // Attach mux event and error event listeners.
          adsManager.addAdErrorListener(adsImaSdkListener);
          adsManager.addAdEventListener(adsImaSdkListener);
        }

        // TODO: probably need to handle some cleanup and things, like removing listeners on destroy
      });
    } catch (ClassNotFoundException cnfe) {
      return;
    }
  }

  // Used in automated tests
  public boolean waitForNextSegmentToLoad(long timeoutInMs) {
    try {
      muxStatsLock.lock();
      return newMediaSegmentStarted.await(timeoutInMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
    } finally {
      muxStatsLock.unlock();
    }
  }

  // ExoPlayer 2.12+ need this to hook add events
  public AdsImaSDKListener getAdsImaSdkListener() {
    return adsImaSdkListener;
  }

  @Deprecated
  public AdsImaSDKListener getAdErrorEventListener() {
    return adsImaSdkListener;
  }

  @Deprecated
  public AdsImaSDKListener getAdEventListener() {
    return adsImaSdkListener;
  }

  @SuppressWarnings("unused")
  public void updateCustomerData(CustomerPlayerData customPlayerData,
      CustomerVideoData customVideoData) {
    muxStats.updateCustomerData(customPlayerData, customVideoData);
  }

  @SuppressWarnings("unused")
  public void updateCustomerData(CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData,
      CustomerViewData customerViewData) {
    muxStats.updateCustomerData(customerPlayerData, customerVideoData, customerViewData);
  }

  @SuppressWarnings("unused")
  public CustomerVideoData getCustomerVideoData() {
    return muxStats.getCustomerVideoData();
  }

  @SuppressWarnings("unused")
  public CustomerPlayerData getCustomerPlayerData() {
    return muxStats.getCustomerPlayerData();
  }

  @SuppressWarnings("unused")
  public CustomerViewData getCustomerViewData() {
    return muxStats.getCustomerViewData();
  }

  public void enableMuxCoreDebug(boolean enable, boolean verbose) {
    muxStats.allowLogcatOutput(enable, verbose);
  }

  @SuppressWarnings("unused")
  public void videoChange(CustomerVideoData customerVideoData) {
    muxStats.videoChange(customerVideoData);
  }

  @SuppressWarnings("unused")
  public void programChange(CustomerVideoData customerVideoData) {
    muxStats.programChange(customerVideoData);
  }

  public void orientationChange(MuxSdkViewOrientation orientation) {
    muxStats.orientationChange(orientation);
  }

  public void setPlayerView(View playerView) {
    this.playerView = new WeakReference<>(playerView);
  }

  @SuppressWarnings("unused")
  public void setPlayerSize(int width, int height) {
    muxStats.setPlayerSize(width, height);
  }

  public void setScreenSize(int width, int height) {
    muxStats.setScreenSize(width, height);
  }

  public void error(MuxErrorException e) {
    muxStats.error(e);
  }

  @SuppressWarnings("unused")
  public void setAutomaticErrorTracking(boolean enabled) {
    muxStats.setAutomaticErrorTracking(enabled);
  }

  public void release() {
    muxStats.release();
    muxStats = null;
    player = null;
  }

  @SuppressWarnings("unused")
  public void setStreamType(int type) {
    streamType = type;
  }

  @Override
  public void dispatch(IEvent event) {
    if (player != null && player.get() != null && muxStats != null) {
      super.dispatch(event);
    }
  }

  @Override
  public long getCurrentPosition() {
    if (playerHandler != null) {
      return playerHandler.getPlayerCurrentPosition();
    }
    return 0;
  }

  @Override
  public String getMimeType() {
    return mimeType;
  }

  @Override
  public Integer getSourceWidth() {
    return sourceWidth;
  }

  @Override
  public Integer getSourceHeight() {
    return sourceHeight;
  }

  @Override
  public Integer getSourceAdvertisedBitrate() {
    return sourceAdvertisedBitrate;
  }

  @Override
  public Float getSourceAdvertisedFramerate() {
    return sourceAdvertisedFramerate;
  }

  @Override
  public Long getSourceDuration() {
    return sourceDuration;
  }

  // State Transitions
  public PlayerState getState() {
    return state;
  }

  protected void configurePlaybackHeadUpdateInterval() {
    if (player == null || player.get() == null) {
      return;
    }

    TrackGroupArray trackGroups = player.get().getCurrentTrackGroups();
    playItemHaveVideoTrack = false;
    if (trackGroups.length > 0) {
      for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
        TrackGroup trackGroup = trackGroups.get(groupIndex);
        if (0 < trackGroup.length) {
          Format trackFormat = trackGroup.getFormat(0);
          if (trackFormat.sampleMimeType != null && trackFormat.sampleMimeType.contains("video")) {
            playItemHaveVideoTrack = true;
            break;
          }
        }
      }
    }
    setPlaybackHeadUpdateInterval();
  }

  protected void setPlaybackHeadUpdateInterval() {
    if (updatePlayheadPositionTimer != null) {
      updatePlayheadPositionTimer.cancel();
    }
    if (playItemHaveVideoTrack) {
      Player.VideoComponent videoComponent = player.get().getVideoComponent();
      videoComponent.setVideoFrameMetadataListener(frameRenderedListener);
    } else {
      // Schedule timer to execute, this is for audio only content.
      updatePlayheadPositionTimer = new Timer();
      updatePlayheadPositionTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          playerHandler.obtainMessage(ExoPlayerHandler.UPDATE_PLAYER_CURRENT_POSITION)
              .sendToTarget();
        }
      }, 0, 15);
    }
  }

  /*
   * This will be called by AdsImaSDKListener to set the player state to: PLAYING_ADS
   * and ADS_PLAYBACK_DONE accordingly
   */
  protected void setState(PlayerState newState) {
    state = newState;
  }

  @Override
  public boolean isBuffering() {
    return getState() == MuxBaseExoPlayer.PlayerState.BUFFERING;
  }

  @Override
  public int getPlayerViewWidth() {
    if (this.playerView != null) {
      View pv = this.playerView.get();
      if (pv != null) {
        return pxToDp(pv.getWidth());
      }
    }
    return 0;
  }

  @Override
  public int getPlayerViewHeight() {
    if (this.playerView != null) {
      View pv = this.playerView.get();
      if (pv != null) {
        return pxToDp(pv.getHeight());
      }
    }
    return 0;
  }

  @Override
  public boolean isPaused() {
    return state == PlayerState.PAUSED || state == PlayerState.ENDED || state == PlayerState.ERROR
        || state == PlayerState.INIT;
  }

  protected void buffering() {
    if (state == PlayerState.REBUFFERING || seekingInProgress
        || state == PlayerState.SEEKED) {
      // ignore
      return;
    }
    // If we are going from playing to buffering then this is rebuffer event
    if (state == PlayerState.PLAYING) {
      rebufferingStarted();
      return;
    }
    // This is initial buffering event before playback starts
    state = PlayerState.BUFFERING;
    dispatch(new TimeUpdateEvent(null));
  }

  protected void pause() {
    if (state == PlayerState.SEEKED) {
      // No pause event after seeked
      return;
    }
    if (state == PlayerState.REBUFFERING) {
      rebufferingEnded();
    }
    if (seekingInProgress) {
      seeked(false);
      return;
    }
    state = PlayerState.PAUSED;
    dispatch(new PauseEvent(null));
  }

  protected void play() {
    if (state == PlayerState.REBUFFERING
        || seekingInProgress
        || state == PlayerState.SEEKED) {
      // Ignore play event after rebuffering and Seeking
      return;
    }
    state = PlayerState.PLAY;
    dispatch(new PlayEvent(null));
  }

  protected void playing() {
    if (seekingInProgress) {
      // We will dispatch playing event after seeked event
      return;
    }
    if (state == PlayerState.PAUSED || state == PlayerState.FINISHED_PLAYING_ADS) {
      play();
    }
    if (state == PlayerState.REBUFFERING) {
      rebufferingEnded();
    }

    state = PlayerState.PLAYING;
    dispatch(new PlayingEvent(null));
  }

  protected void rebufferingStarted() {
    state = PlayerState.REBUFFERING;
    dispatch(new RebufferStartEvent(null));
  }

  protected void rebufferingEnded() {
    dispatch(new RebufferEndEvent(null));
  }

  protected void seeking() {
    if (state == PlayerState.PLAYING) {
      dispatch(new PauseEvent(null));
    }
    state = PlayerState.SEEKING;
    seekingInProgress = true;
    numberOfFramesRenderedSinceSeekingStarted = 0;
    dispatch(new SeekingEvent(null));
  }

  protected void seeked(boolean newFrameRendered) {
    /*
     * Seeked event will be fired by the player immediately after seeking event
     * This is not accurate, instead report the seeked event on first few frames rendered.
     * This function is called each time a new frame is about to be rendered.
     */
    if (seekingInProgress) {
      if (newFrameRendered) {
        if (numberOfFramesRenderedSinceSeekingStarted
            > NUMBER_OF_FRAMES_THAT_ARE_CONSIDERED_PLAYBACK) {
          // This is a playback !!!
          dispatch(new SeekedEvent(null));
          seekingInProgress = false;
          playing();
        } else {
          numberOfFramesRenderedSinceSeekingStarted++;
        }
      } else {
        // the player was seeking while paused
        dispatch(new SeekedEvent(null));
        seekingInProgress = false;
        state = PlayerState.SEEKED;
      }
    }
  }

  protected void ended() {
    dispatch(new PauseEvent(null));
    dispatch(new EndedEvent(null));
    state = PlayerState.ENDED;
  }

  protected void internalError(Exception error) {
    if (error instanceof MuxErrorException) {
      MuxErrorException muxError = (MuxErrorException) error;
      dispatch(new InternalErrorEvent(muxError.getCode(), muxError.getMessage()));
    } else {
      dispatch(new InternalErrorEvent(ERROR_UNKNOWN,
          error.getClass().getCanonicalName() + " - " + error.getMessage()));
    }
  }

  protected void handleRenditionChange(Format format) {
    if (format != null) {
      sourceAdvertisedBitrate = format.bitrate;
      if (format.frameRate > 0) {
        sourceAdvertisedFramerate = format.frameRate;
      }
      sourceWidth = format.width;
      sourceHeight = format.height;
      RenditionChangeEvent event = new RenditionChangeEvent(null);
      dispatch(event);
    }
  }

  static class FrameRenderedListener implements VideoFrameMetadataListener {

    ExoPlayerHandler handler;

    public FrameRenderedListener(ExoPlayerHandler handler) {
      this.handler = handler;
    }

    // As of r2.11.x, the signature for this callback has changed. These are not annotated as @Overrides in
    // order to support both before r2.11.x and after r2.11.x at the same time.
    public void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs,
        Format format) {
      handler.obtainMessage(ExoPlayerHandler.UPDATE_PLAYER_CURRENT_POSITION).sendToTarget();
    }

    public void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs,
        Format format, @Nullable MediaFormat mediaFormat) {
      handler.obtainMessage(ExoPlayerHandler.UPDATE_PLAYER_CURRENT_POSITION).sendToTarget();
    }
  }

  static class ExoPlayerHandler extends Handler {

    static final int UPDATE_PLAYER_CURRENT_POSITION = 1;

    AtomicLong playerCurrentPosition = new AtomicLong(0);
    MuxBaseExoPlayer muxStats;

    public ExoPlayerHandler(Looper looper, MuxBaseExoPlayer muxStats) {
      super(looper);
      this.muxStats = muxStats;
    }

    public long getPlayerCurrentPosition() {
      return playerCurrentPosition.get();
    }

    public void handleMessage(Message msg) {
      switch (msg.what) {
        case UPDATE_PLAYER_CURRENT_POSITION:
          if (muxStats == null || muxStats.player == null) {
            return;
          }
          if (muxStats.player.get() != null) {
            playerCurrentPosition.set(muxStats.player.get().getContentPosition());
          }
          muxStats.seeked(true);
          break;
        default:
          Log.e(TAG, "ExoPlayerHandler>> Unhandled message type: " + msg.what);
      }
    }
  }

  static class MuxDevice implements Idevice {

    private static final String EXO_SOFTWARE = "ExoPlayer";

    static final String CONNECTION_TYPE_CELLULAR = "cellular";
    static final String CONNECTION_TYPE_WIFI = "wifi";
    static final String CONNECTION_TYPE_WIRED = "wired";
    static final String CONNECTION_TYPE_OTHER = "other";

    static final String MUX_DEVICE_ID = "MUX_DEVICE_ID";

    protected WeakReference<Context> contextRef;
    private String deviceId;
    private String appName = "";
    private String appVersion = "";

    MuxDevice(Context ctx) {
      SharedPreferences sharedPreferences = ctx
          .getSharedPreferences(MUX_DEVICE_ID, Context.MODE_PRIVATE);
      deviceId = sharedPreferences.getString(MUX_DEVICE_ID, null);
      if (deviceId == null) {
        deviceId = UUID.randomUUID().toString();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(MUX_DEVICE_ID, deviceId);
        editor.commit();
      }
      contextRef = new WeakReference<>(ctx);
      try {
        PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
        appName = pi.packageName;
        appVersion = pi.versionName;
      } catch (PackageManager.NameNotFoundException e) {
        Log.d(TAG, "could not get package info");
      }
    }

    @Override
    public String getHardwareArchitecture() {
      return Build.HARDWARE;
    }

    @Override
    public String getOsFamily() {
      return "Android";
    }

    @Override
    public String getOsVersion() {
      return Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")";
    }

    @Override
    public String getManufacturer() {
      return Build.MANUFACTURER;
    }

    @Override
    public String getModelName() {
      return Build.MODEL;
    }

    @Override
    public String getPlayerVersion() {
      return ExoPlayerLibraryInfo.VERSION;
    }

    @Override
    public String getDeviceId() {
      return deviceId;
    }

    @Override
    public String getAppName() {
      return appName;
    }

    @Override
    public String getAppVersion() {
      return appVersion;
    }

    @Override
    public String getPluginName() {
      return BuildConfig.MUX_PLUGIN_NAME;
    }

    @Override
    public String getPluginVersion() {
      return BuildConfig.MUX_PLUGIN_VERSION;
    }

    @Override
    public String getPlayerSoftware() {
      return EXO_SOFTWARE;
    }

    @Override
    public String getNetworkConnectionType() {
      // Checking internet connectivity
      Context context = contextRef.get();
      if (context == null) {
        return null;
      }
      ConnectivityManager connectivityMgr = (ConnectivityManager) context
          .getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo activeNetwork = null;
      if (connectivityMgr != null) {
        activeNetwork = connectivityMgr.getActiveNetworkInfo();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          NetworkCapabilities nc = connectivityMgr
              .getNetworkCapabilities(connectivityMgr.getActiveNetwork());
          if (nc == null) {
            Log.e(TAG, "Failed to obtain NetworkCapabilities manager !!!");
            return null;
          }
          if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return CONNECTION_TYPE_WIRED;
          } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return CONNECTION_TYPE_WIFI;
          } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return CONNECTION_TYPE_CELLULAR;
          } else {
            return CONNECTION_TYPE_OTHER;
          }
        } else {
          if (activeNetwork.getType() == ConnectivityManager.TYPE_ETHERNET) {
            return CONNECTION_TYPE_WIRED;
          } else if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
            return CONNECTION_TYPE_WIFI;
          } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
            return CONNECTION_TYPE_CELLULAR;
          } else {
            return CONNECTION_TYPE_OTHER;
          }
        }
      }
      return null;
    }

    @Override
    public long getElapsedRealtime() {
      return elapsedRealtime();
    }

    @Override
    public void outputLog(String tag, String msg) {
      Log.v(tag, msg);
    }
  }

  class BandwidthMetric {

    BandwidthMetricData currentSegmentData;

    public BandwidthMetricData onLoadError(DataSpec dataSpec, int dataType, IOException e) {
      BandwidthMetricData loadData = new BandwidthMetricData();
      loadData.setRequestError(e.toString());
      if (dataSpec != null && dataSpec.uri != null) {
        loadData.setRequestUrl(dataSpec.uri.toString());
        loadData.setRequestHostName(dataSpec.uri.getHost());
      }
      switch (dataType) {
        case C.DATA_TYPE_MANIFEST:
          loadData.setRequestType("manifest");
          break;
        case C.DATA_TYPE_MEDIA:
          loadData.setRequestType("media");
          break;
        default:
          return null;
      }
      loadData.setRequestErrorCode(null);
      loadData.setRequestErrorText(e.getMessage());
      return loadData;
    }

    public BandwidthMetricData onLoadCanceled(DataSpec dataSpec) {
      BandwidthMetricData loadData = new BandwidthMetricData();
      loadData.setRequestCancel("genericLoadCanceled");
      if (dataSpec != null && dataSpec.uri != null) {
        loadData.setRequestUrl(dataSpec.uri.toString());
        loadData.setRequestHostName(dataSpec.uri.getHost());
      }
      loadData.setRequestType("media");
      return loadData;
    }

    protected BandwidthMetricData onLoad(DataSpec dataSpec, int dataType,
        Format trackFormat, long mediaStartTimeMs, long mediaEndTimeMs,
        long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {
      currentSegmentData = new BandwidthMetricData();
      currentSegmentData.setRequestStart(System.currentTimeMillis());
      currentSegmentData.setRequestMediaStartTime(mediaStartTimeMs);
      currentSegmentData.setRequestVideoWidth(sourceWidth);
      currentSegmentData.setRequestVideoHeight(sourceHeight);
      switch (dataType) {
        case C.DATA_TYPE_MANIFEST:
          currentSegmentData.setRequestType("manifest");
          break;
        case C.DATA_TYPE_MEDIA:
          currentSegmentData.setRequestType("media");
          break;
        default:
          return null;
      }
      currentSegmentData.setRequestResponseHeaders(null);
      if (dataSpec != null && dataSpec.uri != null) {
        currentSegmentData.setRequestHostName(dataSpec.uri.getHost());
      }
      if (dataType == C.DATA_TYPE_MEDIA) {
        currentSegmentData.setRequestMediaDuration(mediaEndTimeMs - mediaStartTimeMs);
      }
      if (trackFormat != null) {
        currentSegmentData.setRequestCurrentLevel(null);
        if (dataType == C.DATA_TYPE_MEDIA) {
          currentSegmentData.setRequestMediaStartTime(mediaStartTimeMs);
        }
        currentSegmentData.setRequestVideoWidth(trackFormat.width);
        currentSegmentData.setRequestVideoHeight(trackFormat.height);
      }
      currentSegmentData.setRequestRenditionLists(renditionList);
      return currentSegmentData;
    }

    public BandwidthMetricData onLoadStarted(DataSpec dataSpec, int dataType,
        Format trackFormat, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs) {
      BandwidthMetricData loadData = onLoad(dataSpec, dataType, trackFormat,
          mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs, 0, 0);
      if (loadData != null) {
        loadData.setRequestResponseStart(System.currentTimeMillis());
      }
      return loadData;
    }

    public BandwidthMetricData onLoadCompleted(DataSpec dataSpec, int dataType,
        Format trackFormat, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs,
        long loadDurationMs, long bytesLoaded) {
      currentSegmentData.setRequestBytesLoaded(bytesLoaded);
      currentSegmentData.setRequestBytesLoaded(loadDurationMs);
      if (currentSegmentData != null) {
        currentSegmentData.setRequestResponseEnd(System.currentTimeMillis());
      }
      if (dataSpec != null) {
        // TODO not sure if this is the right value
        currentSegmentData.setRequestMediaDuration(dataSpec.length);
      }
      return currentSegmentData;
    }
  }

  class BandwidthMetricHls extends BandwidthMetric {

    @Override
    public BandwidthMetricData onLoadError(DataSpec dataSpec, int dataType, IOException e) {
      BandwidthMetricData loadData = super.onLoadError(dataSpec, C.DATA_TYPE_MEDIA, e);
      return loadData;
    }

    @Override
    public BandwidthMetricData onLoadCanceled(DataSpec dataSpec) {
      BandwidthMetricData loadData = super.onLoadCanceled(dataSpec);
      loadData.setRequestCancel("hlsFragLoadEmergencyAborted");
      return loadData;
    }

    @Override
    public BandwidthMetricData onLoadCompleted(DataSpec dataSpec, int dataType,
        Format trackFormat, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs,
        long loadDurationMs, long bytesLoaded) {
      BandwidthMetricData loadData = super.onLoadCompleted(dataSpec, dataType, trackFormat,
          mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
      if (loadData != null) {
        if (trackFormat != null) {
          loadData.setRequestLabeledBitrate(trackFormat.bitrate);
        }
      } else {
        Log.e(TAG, "FUCK !!!");
      }
      return loadData;
    }
  }


  class BandwidthMetricDispatcher {

    private final BandwidthMetric bandwidthMetricHls = new BandwidthMetricHls();
//    private final BandwidthMetric bandwidthMetricDash = new BandwidthMetricDash();


    public BandwidthMetric currentBandwidthMetric() {
      // TODO see if we need different data for HLS and for DASH
//      if (streamType == -1) {
//        detectStreamType();
//      }
//      switch (streamType) {
//        case C.TYPE_HLS:
//          return bandwidthMetricHls;
//        case C.TYPE_DASH:
//          return bandwidthMetricDash;
//        default:
//          break;
//      }
//      return null;
      return bandwidthMetricHls;
    }

    public void onLoadError(DataSpec dataSpec, int dataType, IOException e) {
      Log.e(TAG, "onLoadError");
      if (player == null || player.get() == null || muxStats == null
          || currentBandwidthMetric() == null) {
        return;
      }
      BandwidthMetricData loadData = currentBandwidthMetric().onLoadError(dataSpec, dataType, e);
      dispatch(loadData, new RequestFailed(null));
    }

    public void onLoadCanceled(DataSpec dataSpec) {
      if (player == null || player.get() == null || muxStats == null
          || currentBandwidthMetric() == null) {
        return;
      }
      BandwidthMetricData loadData = currentBandwidthMetric().onLoadCanceled(dataSpec);
      dispatch(loadData, new RequestCanceled(null));
    }

    public void onLoadStarted(DataSpec dataSpec, int dataType, Format trackFormat,
        long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs) {
      if (player == null || player.get() == null || muxStats == null
          || currentBandwidthMetric() == null) {
        return;
      }
      currentBandwidthMetric().onLoadStarted(dataSpec, dataType,
          trackFormat, mediaStartTimeMs,
          mediaEndTimeMs, elapsedRealtimeMs);
    }

    public void onLoadCompleted(DataSpec dataSpec, int dataType, Format trackFormat,
        long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs,
        long bytesLoaded, Map<String, List<String>> responseHeaders) {
      if (player == null || player.get() == null || muxStats == null
          || currentBandwidthMetric() == null) {
        return;
      }
      BandwidthMetricData loadData = currentBandwidthMetric().onLoadCompleted(dataSpec,
          dataType,
          trackFormat, mediaStartTimeMs,
          mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs, bytesLoaded);

      // Only append this data if we have some load data going on already
      // TODO - this does not work correctly today, fix this and re-enable it.
//            if (loadData != null && responseHeaders != null) {
//                Hashtable<String, String> headers = parseHeaders(responseHeaders);
//
//                if (headers != null) {
//                    loadData.setRequestResponseHeaders(headers);
//                }
//            }

      dispatch(loadData, new RequestCompleted(null));
    }

    public void onTracksChanged(TrackGroupArray trackGroups) {
      if (player == null || player.get() == null || muxStats == null
          || currentBandwidthMetric() == null) {
        return;
      }
      if (trackGroups.length > 0) {
        for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
          TrackGroup trackGroup = trackGroups.get(groupIndex);
          if (0 < trackGroup.length) {
            Format trackFormat = trackGroup.getFormat(0);
            if (trackFormat.containerMimeType != null && trackFormat.containerMimeType
                .contains("video")) {
              List<BandwidthMetricData.Rendition> renditions = new ArrayList<>();
              for (int i = 0; i < trackGroup.length; i++) {
                trackFormat = trackGroup.getFormat(i);
                BandwidthMetricData.Rendition rendition = new BandwidthMetricData.Rendition();
                rendition.bitrate = trackFormat.bitrate;
                rendition.width = trackFormat.width;
                rendition.height = trackFormat.height;
                renditions.add(rendition);
              }
              renditionList = renditions;
            }
          }
        }
      }
    }

    private void dispatch(BandwidthMetricData data, PlaybackEvent event) {
      if (data != null) {
        event.setBandwidthMetricData(data);
        MuxBaseExoPlayer.this.dispatch(event);
      }
    }

    private Hashtable<String, String> parseHeaders(Map<String, List<String>> responseHeaders) {
      if (responseHeaders == null || responseHeaders.size() == 0) {
        return null;
      }

      Hashtable<String, String> headers = new Hashtable<String, String>();
      for (String headerName : responseHeaders.keySet()) {
        if (headerName == null) {
          continue;
        }
        List<String> headerValues = responseHeaders.get(headerName);
        if (headerValues.size() == 1) {
          headers.put(headerName, headerValues.get(0));
        } else if (headerValues.size() > 1) {
          // In the case that there is more than one header, we squash
          // it down to a single comma-separated value per RFC 2616
          // https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2
          String headerValue = headerValues.get(0);
          for (int i = 1; i < headerValues.size(); i++) {
            headerValue = headerValue + ", " + headerValues.get(i);
          }
          headers.put(headerName, headerValue);
        }
      }
      return headers;
    }

    private void detectStreamType() {
      // This is a hack that may not work so well whit urls that do not end with clear extensions
      // TODO see if there is a better way to do this
      MediaItem media = player.get().getCurrentMediaItem();
      Uri uri = Uri.parse(media.mediaId);
      @C.ContentType int type = Util.inferContentType(uri, null);
      streamType = type;
    }
  }

  protected void detectStreamType(Format format) {
    // This is reliable way to detect stream type, but it get called a bit too late,
    // some media segments are already loaded by the time we detect the stream format
    if (format != null && format.metadata != null &&
        format.metadata.length() > 0) {
      for (int i = 0; i < format.metadata.length(); i++) {
        if (format.metadata.get(i) instanceof HlsTrackMetadataEntry) {
          streamType = C.TYPE_HLS;
        }
        // TODO detect DASH
      }
    }
  }

  private int pxToDp(int px) {
    Context context = contextRef.get();

    // Bail out if we don't have the context
    if (context == null) {
      Log.d(TAG, "Error retrieving Context for logical resolution, using physical");
      return px;
    }

    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
    return (int) Math.ceil(px / displayMetrics.density);
  }

  protected BandwidthMetricDispatcher bandwidthDispatcher = new BandwidthMetricDispatcher();
  protected List<BandwidthMetricData.Rendition> renditionList;
}
