package com.mux.stats.sdk.muxstats;

import static android.os.SystemClock.elapsedRealtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaFormat;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
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
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoListener;
import com.mux.stats.sdk.core.MuxSDKViewOrientation;
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
import com.mux.stats.sdk.core.events.playback.RequestCanceled;
import com.mux.stats.sdk.core.events.playback.RequestCompleted;
import com.mux.stats.sdk.core.events.playback.RequestFailed;
import com.mux.stats.sdk.core.events.playback.SeekedEvent;
import com.mux.stats.sdk.core.events.playback.SeekingEvent;
import com.mux.stats.sdk.core.events.playback.TimeUpdateEvent;
import com.mux.stats.sdk.core.model.BandwidthMetricData;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.core.model.CustomerViewData;
import com.mux.stats.sdk.core.util.MuxLogger;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class MuxBaseExoPlayer extends EventBus implements IPlayerListener {

  protected static final String TAG = "MuxStatsListener";
  // Error codes start at -1 as ExoPlaybackException codes start at 0 and go up.
  protected static final int ERROR_UNKNOWN = -1;
  protected static final int ERROR_DRM = -2;
  protected static final int ERROR_IO = -3;

  protected static final long TIME_TO_WAIT_AFTER_FIRST_FRAME_RENDERED = 50; // in ms

  protected String mimeType;
  protected Integer sourceWidth;
  protected Integer sourceHeight;
  protected Integer sourceAdvertisedBitrate;
  protected Float sourceAdvertisedFramerate;
  protected Long sourceDuration;
  protected ExoPlayerHandler playerHandler;
  protected Timer updatePlayheadPositionTimer;
  protected MuxVideoListener videoListener;

  protected WeakReference<ExoPlayer> player;
  protected WeakReference<View> playerView;
  protected WeakReference<Context> contextRef;
  protected AdsImaSDKListener adsImaSdkListener;

  protected boolean detectMimeType;
  protected boolean firstFrameReceived = false;
  protected int numberOfEventsSent = 0;
  protected int numberOfPlayEventsSent = 0;
  protected int numberOfPauseEventsSent = 0;
  protected int streamType = -1;
  protected long firstFrameRenderedAt = -1;

  public enum PlayerState {
    BUFFERING, REBUFFERING, SEEKING, SEEKED, ERROR, PAUSED, PLAY, PLAYING, PLAYING_ADS,
    FINISHED_PLAYING_ADS, INIT, ENDED
  }

  protected PlayerState state;
  protected MuxStats muxStats;
  boolean seekingInProgress;
  boolean playItemHaveVideoTrack;


  @Deprecated
  MuxBaseExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData, CustomerVideoData customerVideoData,
      CustomerViewData customerViewData, boolean sentryEnabled,
      INetworkRequest networkRequest) {
    this(ctx, player, playerName,
        new CustomerData(customerPlayerData, customerVideoData, customerViewData),
        sentryEnabled, networkRequest);
  }

  MuxBaseExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerData data, boolean sentryEnabled,
      INetworkRequest networkRequest) {
    super();
    detectMimeType = true;
    this.player = new WeakReference<>(player);
    this.contextRef = new WeakReference<>(ctx);
    state = PlayerState.INIT;
    MuxStats.setHostDevice(new MuxDevice(ctx));
    MuxStats.setHostNetworkApi(networkRequest);
    muxStats = new MuxStats(this, playerName, data, sentryEnabled);
    addListener(muxStats);
    playerHandler = new ExoPlayerHandler(player.getApplicationLooper(), this);
    videoListener = new MuxVideoListener(this);
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
      MuxLogger.d(TAG, "Null AdsLoader provided to monitorImaAdsLoader");
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

  protected void allowHeaderToBeSentToBackend(String headerName) {
    synchronized (bandwidthDispatcher) {
      bandwidthDispatcher.allowedHeaders.add(headerName);
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
  public void updateCustomerData(CustomerData data) {
    muxStats.setCustomerData(data);
  }

  @SuppressWarnings("unused")
  public CustomerData getCustomerData() {
    return muxStats.getCustomerData();
  }

  @Deprecated
  @SuppressWarnings("unused")
  public void updateCustomerData(CustomerPlayerData customPlayerData,
      CustomerVideoData customVideoData) {
    muxStats.updateCustomerData(customPlayerData, customVideoData);
  }

  @Deprecated
  @SuppressWarnings("unused")
  public void updateCustomerData(CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData,
      CustomerViewData customerViewData) {
    muxStats.updateCustomerData(customerPlayerData, customerVideoData, customerViewData);
  }

  @Deprecated
  @SuppressWarnings("unused")
  public CustomerVideoData getCustomerVideoData() {
    return muxStats.getCustomerVideoData();
  }

  @Deprecated
  @SuppressWarnings("unused")
  public CustomerPlayerData getCustomerPlayerData() {
    return muxStats.getCustomerPlayerData();
  }

  @Deprecated
  @SuppressWarnings("unused")
  public CustomerViewData getCustomerViewData() {
    return muxStats.getCustomerViewData();
  }

  public void enableMuxCoreDebug(boolean enable, boolean verbose) {
    muxStats.allowLogcatOutput(enable, verbose);
  }

  @SuppressWarnings("unused")
  public void videoChange(CustomerVideoData customerVideoData) {
    // Reset the state to avoid unwanted rebuffering events
    state = PlayerState.INIT;
    resetInternalStats();
    muxStats.videoChange(customerVideoData);
  }

  @SuppressWarnings("unused")
  public void programChange(CustomerVideoData customerVideoData) {
    resetInternalStats();
    muxStats.programChange(customerVideoData);
  }

  public void orientationChange(MuxSDKViewOrientation orientation) {
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
      numberOfEventsSent++;
      if (event.getType().equalsIgnoreCase(PlayEvent.TYPE)) {
        numberOfPlayEventsSent++;
      }
      if (event.getType().equalsIgnoreCase(PauseEvent.TYPE)) {
        numberOfPauseEventsSent++;
      }
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
      ExoPlayer.VideoComponent videoComponent = player.get().getVideoComponent();
      videoComponent.addVideoListener(videoListener);
    }
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
    if (state == PlayerState.SEEKED && numberOfPauseEventsSent > 0) {
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
    // If this is the first play event it may be very important not to be skipped
    // In all other cases skip this play event
    if (
        (state == PlayerState.REBUFFERING
            || seekingInProgress
            || state == PlayerState.SEEKED) &&
            (numberOfPlayEventsSent > 0)
    ) {
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
    firstFrameRenderedAt = -1;
    dispatch(new SeekingEvent(null));
    firstFrameReceived = false;
  }

  protected void seeked(boolean timeUpdateEvent) {
    /*
     * Seeked event will be fired by the player immediately after seeking event
     * This is not accurate, instead report the seeked event on first few frames rendered.
     * This function is called each time a new frame is about to be rendered.
     */
    if (seekingInProgress) {
      if (timeUpdateEvent) {
        if ((System.currentTimeMillis() - firstFrameRenderedAt
            > TIME_TO_WAIT_AFTER_FIRST_FRAME_RENDERED) && firstFrameReceived) {
          // This is a playback !!!
          dispatch(new SeekedEvent(null));
          seekingInProgress = false;
          playing();
        } else {
          // No playback yet.
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

  private void resetInternalStats() {
    detectMimeType = true;
    numberOfPauseEventsSent = 0;
    numberOfPlayEventsSent = 0;
    numberOfEventsSent = 0;
    firstFrameReceived = false;
    firstFrameRenderedAt = -1;
  }

  static class MuxVideoListener implements VideoListener {
    MuxBaseExoPlayer muxStats;

    public MuxVideoListener(MuxBaseExoPlayer muxStats) {
      this.muxStats = muxStats;
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio) {
      // Do nothing
    }

    @Override
    public void onSurfaceSizeChanged(int width, int height) {
      // Do nothing
    }

    @Override
    public void onRenderedFirstFrame() {
      // TODO save this timestamp
      muxStats.firstFrameRenderedAt = System.currentTimeMillis();
      muxStats.firstFrameReceived = true;
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
          if (muxStats.seekingInProgress) {
            muxStats.seeked(true);
          }
          break;
        default:
          MuxLogger.d(TAG, "ExoPlayerHandler>> Unhandled message type: " + msg.what);
      }
    }
  }

  static class MuxDevice implements IDevice {

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
        MuxLogger.d(TAG, "could not get package info");
      }
    }

    @Override
    public String getHardwareArchitecture() {
      return Build.HARDWARE;
    }

    @Override
    public String getOSFamily() {
      return "Android";
    }

    @Override
    public String getOSVersion() {
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
            MuxLogger.d(TAG, "ERROR: Failed to obtain NetworkCapabilities manager !!!");
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

    TrackGroupArray availableTracks;
    HashMap<String, BandwidthMetricData> loadedSegments = new HashMap<>();

    public BandwidthMetricData onLoadError(String segmentUrl,
        IOException e) {
      BandwidthMetricData segmentData = loadedSegments.get(segmentUrl);
      if (segmentData == null) {
        segmentData = new BandwidthMetricData();
        // TODO We should see how to put minimal stats here !!!
      }
      segmentData.setRequestError(e.toString());
      // TODO see what error codes are
      segmentData.setRequestErrorCode(-1);
      segmentData.setRequestErrorText(e.getMessage());
      segmentData.setRequestResponseEnd(System.currentTimeMillis());
      return segmentData;
    }

    public BandwidthMetricData onLoadCanceled(String segmentUrl) {
      BandwidthMetricData segmentData = loadedSegments.get(segmentUrl);
      if (segmentData == null) {
        segmentData = new BandwidthMetricData();
        // TODO We should see how to put minimal stats here !!!
      }
      segmentData.setRequestCancel("genericLoadCanceled");
      segmentData.setRequestResponseEnd(System.currentTimeMillis());
      return segmentData;
    }

    protected BandwidthMetricData onLoad(long mediaStartTimeMs, long mediaEndTimeMs,
        String segmentUrl, int dataType, String host, String segmentMimeType
    ) {
      BandwidthMetricData segmentData = new BandwidthMetricData();
      // TODO RequestStart timestamp is currently not available from ExoPlayer
      segmentData.setRequestResponseStart(System.currentTimeMillis());
      segmentData.setRequestMediaStartTime(mediaStartTimeMs);
      segmentData.setRequestVideoWidth(sourceWidth);
      segmentData.setRequestVideoHeight(sourceHeight);
      segmentData.setRequestUrl(segmentUrl);
      switch (dataType) {
        case C.DATA_TYPE_MANIFEST:
          detectMimeType = false;
        case C.DATA_TYPE_MEDIA_INITIALIZATION:
          if (segmentMimeType.contains("video")) {
            segmentData.setRequestType("video_init");
          } else if (segmentMimeType.contains("audio")) {
            segmentData.setRequestType("audio_init");
          } else {
            segmentData.setRequestType("manifest");
          }
          break;
        case C.DATA_TYPE_MEDIA:
          segmentData.setRequestType("media");
          segmentData.setRequestMediaDuration(mediaEndTimeMs
              - mediaStartTimeMs);
          break;
        default:
      }
      segmentData.setRequestResponseHeaders(null);
      segmentData.setRequestHostName(host);
      segmentData.setRequestRenditionLists(renditionList);
      loadedSegments.put(segmentUrl, segmentData);
      return segmentData;
    }

    public BandwidthMetricData onLoadStarted(long mediaStartTimeMs, long mediaEndTimeMs,
        String segmentUrl, int dataType, String host, String segmentMimeType) {
      BandwidthMetricData loadData = onLoad(mediaStartTimeMs, mediaEndTimeMs, segmentUrl
          , dataType, host, segmentMimeType);
      if (loadData != null) {
        loadData.setRequestResponseStart(System.currentTimeMillis());
      }
      return loadData;
    }

    public BandwidthMetricData onLoadCompleted(String segmentUrl, long bytesLoaded,
        Format trackFormat) {
      BandwidthMetricData segmentData = loadedSegments.get(segmentUrl);
      if (segmentData == null) {
        return null;
      }
      segmentData.setRequestBytesLoaded(bytesLoaded);
      segmentData.setRequestResponseEnd(System.currentTimeMillis());
      if (trackFormat != null && availableTracks != null) {
        for (int i = 0; i < availableTracks.length; i++) {
          TrackGroup tracks = availableTracks.get(i);
          for (int trackGroupIndex = 0; trackGroupIndex < tracks.length; trackGroupIndex++) {
            Format currentFormat = tracks.getFormat(trackGroupIndex);
            if (trackFormat.width == currentFormat.width
                && trackFormat.height == currentFormat.height
                && trackFormat.bitrate == currentFormat.bitrate) {
              segmentData.setRequestCurrentLevel(trackGroupIndex);
            }
          }
        }
      }
      loadedSegments.remove(segmentUrl);
      return segmentData;
    }
  }

  class BandwidthMetricHls extends BandwidthMetric {

    @Override
    public BandwidthMetricData onLoadError(String segmentUrl,
        IOException e) {
      BandwidthMetricData loadData = super.onLoadError(segmentUrl, e);
      return loadData;
    }

    @Override
    public BandwidthMetricData onLoadCanceled(String segmentUrl) {
      BandwidthMetricData loadData = super.onLoadCanceled(segmentUrl);
      loadData.setRequestCancel("hlsFragLoadEmergencyAborted");
      return loadData;
    }

    @Override
    public BandwidthMetricData onLoadCompleted(String segmentUrl,
        long bytesLoaded,
        Format trackFormat) {
      BandwidthMetricData loadData = super.onLoadCompleted(segmentUrl, bytesLoaded, trackFormat);
      if (trackFormat != null && loadData != null) {
        loadData.setRequestLabeledBitrate(trackFormat.bitrate);
      }
      return loadData;
    }
  }


  class BandwidthMetricDispatcher {

    private final BandwidthMetric bandwidthMetricHls = new BandwidthMetricHls();
    ArrayList<String> allowedHeaders = new ArrayList<>();

    public BandwidthMetricDispatcher() {
      allowedHeaders.add("x-cdn");
      allowedHeaders.add("content-type");
    }

    public BandwidthMetric currentBandwidthMetric() {
      return bandwidthMetricHls;
    }

    public void onLoadError(String segmentUrl, IOException e) {
      if (player == null || player.get() == null || muxStats == null
          || currentBandwidthMetric() == null) {
        return;
      }
      BandwidthMetricData loadData = currentBandwidthMetric().onLoadError(segmentUrl, e);
      dispatch(loadData, new RequestFailed(null));
    }

    public void onLoadCanceled(String segmentUrl, Map<String, List<String>> headers) {
      if (player == null || player.get() == null || muxStats == null
          || currentBandwidthMetric() == null) {
        return;
      }
      BandwidthMetricData loadData = currentBandwidthMetric().onLoadCanceled(segmentUrl);
      parseHeaders(loadData, headers);
      dispatch(loadData, new RequestCanceled(null));
    }

    public void onLoadStarted(long mediaStartTimeMs, long mediaEndTimeMs, String segmentUrl,
        int dataType, String host, String segmentMimeType) {
      if (player == null || player.get() == null || muxStats == null
          || currentBandwidthMetric() == null) {
        return;
      }
      currentBandwidthMetric().onLoadStarted(mediaStartTimeMs, mediaEndTimeMs, segmentUrl
          , dataType, host, segmentMimeType);
    }

    public void onLoadCompleted(
        String segmentUrl, long bytesLoaded, Format trackFormat,
        Map<String, List<String>> responseHeaders) {
      if (player == null || player.get() == null || muxStats == null
          || currentBandwidthMetric() == null) {
        return;
      }
      BandwidthMetricData loadData = currentBandwidthMetric().onLoadCompleted(
          segmentUrl, bytesLoaded, trackFormat);
      if (loadData != null) {
        parseHeaders(loadData, responseHeaders);
        dispatch(loadData, new RequestCompleted(null));
      }
    }

    private void parseHeaders(BandwidthMetricData loadData,
        Map<String, List<String>> responseHeaders) {
      if (responseHeaders != null) {
        Hashtable<String, String> headers = parseHeaders(responseHeaders);

        if (headers != null) {
          loadData.setRequestResponseHeaders(headers);
        }
      }
    }

    public void onTracksChanged(TrackGroupArray trackGroups) {
      currentBandwidthMetric().availableTracks = trackGroups;
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
        boolean headerAllowed = false;
        synchronized (this) {
          for (String allowedHeader : allowedHeaders) {
            if (allowedHeader.equalsIgnoreCase(headerName)) {
              headerAllowed = true;
            }
          }
        }
        if (!headerAllowed) {
          // Pass this header, we do not need it
          continue;
        }

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
  }

  private int pxToDp(int px) {
    Context context = contextRef.get();

    // Bail out if we don't have the context
    if (context == null) {
      MuxLogger.d(TAG, "Error retrieving Context for logical resolution, using physical");
      return px;
    }

    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
    return (int) Math.ceil(px / displayMetrics.density);
  }

  protected BandwidthMetricDispatcher bandwidthDispatcher = new BandwidthMetricDispatcher();
  protected List<BandwidthMetricData.Rendition> renditionList;
}
