package com.mux.stats.sdk.muxstats;

import static android.os.SystemClock.elapsedRealtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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

import androidx.annotation.RequiresApi;

import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.common.base.Objects;
import com.mux.stats.sdk.core.CustomOptions;
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
import com.mux.stats.sdk.core.model.SessionData;
import com.mux.stats.sdk.core.util.MuxLogger;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class connects the {@link ExoPlayer}, {@link MuxStats} and
 * {@link com.google.ads.interactivemedia.v3.api}.
 *
 * The actual hook to data statistics for {@link ExoPlayer} is implemented here
 * {@link MuxStatsExoPlayer}, this class is base logic for event processing that is independent
 * of {@link ExoPlayer} version and make sure that all events are passed to {@link MuxStats} in
 * the correct order and with appropriate statistics.
 */
public abstract class MuxBaseExoPlayer extends EventBus implements IPlayerListener {

  protected static final String TAG = "MuxStatsListener";

  /** Error codes start at -1 and go down as ExoPlaybackException codes start at 0 and go up. */
  protected static final int ERROR_UNKNOWN = -1;
  protected static final int ERROR_DRM = -2;
  protected static final int ERROR_IO = -3;

  /**
   * This is the time to wait in ms that needs to pass after the player has seeked in
   * order for us to conclude that playback has actually started. We use this value in a workaround
   * to detect the playing event when {@link SeekedEvent} event is some times reported by
   * {@link ExoPlayer} a few milliseconds after the {@link PlayingEvent} which is not what is
   * expected.
   * */
  protected static final long TIME_TO_WAIT_AFTER_FIRST_FRAME_RENDERED = 50; // in ms

  /**
   * This variable stores the mime type of the current playback container. It is not possible to
   * detect for dash and HLS playback containers because {@link ExoPlayer} reports the mime type of
   * segment container. In this case we do not set this value and the video mime type is calculated
   * on the backend based on the url.
   */
  protected String mimeType;
  /** This is the width of the current playback video resolution. */
  protected Integer sourceWidth;
  /** This is the height of the current playback video resolution. */
  protected Integer sourceHeight;
  /**
   * This is the bitrate declared in the playback container. This can be different from the actual
   * calculated bitrate.
   */
  protected Integer sourceAdvertisedBitrate;
  /**
   * This is the framerate declared in video container. It can be different from the actual
   * framerate
   */
  protected Float sourceAdvertisedFramerate;
  /**
   * This is the duration of current media segment in milliseconds. This value is extracted from the
   * Media container and can also be different from actual Media duration.
   */
  protected Long sourceDuration;
  /** Store all time detailes of current segment */
  protected Timeline.Window currentTimelineWindow = new Window();
  /** This is used to update the current playback position in real time. */
  protected ExoPlayerHandler playerHandler;
  protected Timer updatePlayheadPositionTimer;
  // Detect basic hardware details.
  protected MuxDevice muxDevice;

  /** Here we store the {@link ExoPlayer} instance. */
  protected WeakReference<ExoPlayer> player;
  /** This is the UI object that contains the rendering surface. */
  protected WeakReference<View> playerView;
  /** Activity context. */
  protected WeakReference<Context> contextRef;
  /** Ad monitoring and processing logic. */
  protected AdsImaSDKListener adsImaSdkListener;

  /** Event counter. This is useful to know when the view have started. */
  protected boolean detectMimeType;
  protected boolean firstFrameReceived = false;
  protected int numberOfEventsSent = 0;
  /** Number of {@link PlayingEvent} sent since the View started. */
  protected int numberOfPlayEventsSent = 0;
  /** Number of {@link PauseEvent} sent since the View started. */
  protected int numberOfPauseEventsSent = 0;
  /**
   * This was used to determine if we are playing HLS or DASH. It is not possible to automatically
   * detect this so we exposed the {@link #setStreamType(int)} method and we expect the application
   * layer to set this value properly.
   *
   * At this point we make no difference in data collection between HLS and DASH so this value is
   * not needed.
   */
  @Deprecated
  protected int streamType = -1;
  protected long firstFrameRenderedAt = -1;

  protected static final Pattern RX_SESSION_TAG_DATA_ID = Pattern.compile("DATA-ID=\"(.*)\",");
  protected static final Pattern RX_SESSION_TAG_VALUES = Pattern.compile("VALUE=\"(.*)\"");
  /** HLS session data tags with this Data ID will be sent to Mux Data */
  protected static final String HLS_SESSION_DATA_PREFIX = "io.litix.data.";
  /** If playing HLS, Contains the EXT-X-SESSION info for the video being played */
  protected List<SessionData> sessionTags = new LinkedList<>();

  /**
   * These are the different playback states that are monitored internally. {@link ExoPlayer} keeps
   * its own internal state which sometimes can be different then one described here.
   */
  public enum PlayerState {
    BUFFERING, REBUFFERING, SEEKING, SEEKED, ERROR, PAUSED, PLAY, PLAYING, PLAYING_ADS,
    FINISHED_PLAYING_ADS, INIT, ENDED
  }

  /** Current playback state described here: {@link PlayerState} */
  protected PlayerState state;
  /** Underlying MuxCore interface. */
  protected MuxStats muxStats;
  /**
   * Set to true if the player is currently seeking. This also include all necessary network
   * buffering to start the playback from new position.
   */
  boolean seekingInProgress;
  /** This value is set to true if {@link com.google.android.exoplayer2.MediaItem} has video. */
  boolean playItemHaveVideoTrack;

  /**
   * Basic constructor.
   *
   * @param ctx Activity context.
   * @param player ExoPlayer to monitor, must be unique per {@link MuxBaseExoPlayer} instance. Two
   *               different instances can not monitor the same player at the same time. As soon as
   *               a second instance is created the first instance will stop receiving events from
   *               the player
   * @param playerName This is the unique player id and it is a static field. Each instance must
   *                   have a unique player name in the same process.
   * @param customerPlayerData basic playback data set by the Application.
   * @param customerVideoData basic Video data set by the Application.
   * @param customerViewData basic View data set by the application.
   * @param unused Unused parameter. Prefer to use {@link #MuxBaseExoPlayer(Context, ExoPlayer, String, CustomerData, CustomOptions, INetworkRequest)}
   * @param networkRequest internet interface implementation.
   *
   * @deprecated Prefer to use {@link #MuxBaseExoPlayer(Context, ExoPlayer, String, CustomerData, CustomOptions, INetworkRequest)}
   */
  @Deprecated
  MuxBaseExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData, CustomerVideoData customerVideoData,
      CustomerViewData customerViewData, @Deprecated boolean unused,
      INetworkRequest networkRequest) {
    this(ctx, player, playerName,
        new CustomerData(customerPlayerData, customerVideoData, customerViewData),
            false, networkRequest);
    // TODO: em - This ctor looks unused and internal. Should it be removed?
  }

    /**
     * Basic constructor.
     *
     * @param ctx Activity context.
     * @param player ExoPlayer to monitor, must be unique per {@link MuxBaseExoPlayer} instance. Two
     *               different instances can not monitor the same player at the same time. As soon as
     *               a second instance is created the first instance will stop receiving events from
     *               the player
     * @param playerName This is the unique player id and it is a static field. Each instance must
     *                   have a unique player name in the same process.
     * @param data Customer, View, and Video data set by the user
     * @param unused Unused parameter. Prefer to use {@link #MuxBaseExoPlayer(Context, ExoPlayer, String, CustomerData, CustomOptions, INetworkRequest)}
     * @param networkRequest internet interface implementation.
     *
     * @deprecated Prefer to use {@link #MuxBaseExoPlayer(Context, ExoPlayer, String, CustomerData, CustomOptions, INetworkRequest)}
     */
  @Deprecated
  MuxBaseExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerData data, @Deprecated boolean unused,
      INetworkRequest networkRequest) {
    this(ctx, player, playerName, data, new CustomOptions(), networkRequest);
    // TODO: em - This ctor looks unused and internal. Should it be removed?
  }

    /**
     * Basic constructor.
     *
     * @param ctx Activity context.
     * @param player ExoPlayer to monitor, must be unique per {@link MuxBaseExoPlayer} instance. Two
     *               different instances can not monitor the same player at the same time. As soon as
     *               a second instance is created the first instance will stop receiving events from
     *               the player
     * @param playerName This is the unique player id and it is a static field. Each instance must
     *                   have a unique player name in the same process.
     * @param data Customer, View, and Video data set by the user
     * @param options Custom Options for configuring the SDK
     * @param networkRequest internet interface implementation.
     *
     * @deprecated Prefer to use {@link #MuxBaseExoPlayer(Context, ExoPlayer, String, CustomerData, CustomOptions, INetworkRequest)}
     */
  MuxBaseExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerData data, CustomOptions options,
      INetworkRequest networkRequest) {
    super();
    detectMimeType = true;
    this.player = new WeakReference<>(player);
    this.contextRef = new WeakReference<>(ctx);
    state = PlayerState.INIT;
    muxDevice = new MuxDevice(ctx);
    MuxStats.setHostDevice(muxDevice);
    MuxStats.setHostNetworkApi(networkRequest);
    muxStats = new MuxStats(this, playerName, data, options);
    addListener(muxStats);
    playerHandler = new ExoPlayerHandler(player.getApplicationLooper(), this);
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

  protected abstract boolean isLivePlayback();

  protected abstract String parseHlsManifestTag(String tagName);

  protected List<String> filterHlsSessionTags(List<String> rawTags) {
    //noinspection ConstantConditions
    return Util.filter(rawTags, new ArrayList<>(), tag -> tag.substring(1).startsWith("EXT-X-SESSION-DATA"));
  }

  protected void onMainPlaylistTags(List<String> playlistTags) {
    List<SessionData> newSessionData = new LinkedList<>();
    for(String tag : filterHlsSessionTags(playlistTags)) {
      SessionData data = parseHlsSessionData(tag);
      if(data.key.startsWith(HLS_SESSION_DATA_PREFIX)) {
        newSessionData.add(data);
      }
    }

    // dispatch new session data on change only
    if(!newSessionData.equals(sessionTags)) {
      sessionTags = newSessionData;
      // TODO: Dispatch Session Data Event
      Map<String, String> tagMap = new HashMap<>();
      for(SessionData data: sessionTags) {
        tagMap.put(data.key, data.value);
      }
    }
  }

  protected SessionData parseHlsSessionData(String line) {
    Matcher dataId = RX_SESSION_TAG_DATA_ID.matcher(line);
    Matcher value = RX_SESSION_TAG_VALUES.matcher(line);
    String parsedDataId = "";
    String parsedValue = "";

    if(dataId.find()) {
      parsedDataId = dataId.group(1);
    } else {
      MuxLogger.d(TAG, "Data-ID not found in session data: " + line);
    }
    if(value.find()) {
      parsedValue = value.group(1);
    } else {
      MuxLogger.d(TAG, "Value not found in session data: " + line);
    }

    return new SessionData(parsedDataId, parsedValue);
  }

  /**
   * Allow HTTP headers with a given name to be passed to the backend. By default we ignore all HTTP
   * headers that are not in the {@link BandwidthMetricDispatcher#allowedHeaders} list.
   * This is used in automated tests and is not intended to be used from the application layer.
   *
   * @param headerName name of the header to send to the backend.
   */
  protected void allowHeaderToBeSentToBackend(String headerName) {
    synchronized (bandwidthDispatcher) {
      bandwidthDispatcher.allowedHeaders.add(headerName);
    }
  }

  /**
   * ExoPlayer 2.12+ need this to hook add events.
   */
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

  /**
   * Value used here will be reported to the backend as a device name. If this method is not used
   * then SDK will auto detect the device name. State will be reseted on every new view or video.
   * @param deviceName, name to be used instead of auto detected value.
   */
  public void overwriteDeviceMetadata(String deviceName) {
    if (muxDevice != null) {
      muxDevice.overwriteDeviceMetadata(deviceName);
    }
  }

  /**
   * Update the Application set data. If any of the given arguments is null it will not override the
   * existing data.
   *
   * @param customPlayerData new data to be used.
   * @param customVideoData new data to be used.
   */
  @Deprecated
  @SuppressWarnings("unused")
  public void updateCustomerData(CustomerPlayerData customPlayerData,
      CustomerVideoData customVideoData) {
    muxStats.updateCustomerData(customPlayerData, customVideoData);
  }

  /**
   * Update the Application set data. If any of the given arguments is null it will not override the
   * existing data.
   *
   * @param customerPlayerData new data to be used.
   * @param customerVideoData new data to be used.
   * @param customerViewData new data to be used.
   */
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

  /**
   * If set to true the underlying {@link MuxStats} logs will be output in the logcat.
   *
   * @param enable if set to true the log will be  printed in logcat.
   * @param verbose if set to true each event will be printed with all stats, this output can be
   *                overwhelming
   */
  public void enableMuxCoreDebug(boolean enable, boolean verbose) {
    muxStats.allowLogcatOutput(enable, verbose);
  }

  /**
   * This method is called when {@link ExoPlayer} {@link com.google.android.exoplayer2.MediaItem}
   * item is changed and we need to start a new view.
   *
   * @param customerVideoData new video data.
   */
  @SuppressWarnings("unused")
  public void videoChange(CustomerVideoData customerVideoData) {
    // Reset the state to avoid unwanted rebuffering events
    state = PlayerState.INIT;
    resetInternalStats();
    muxStats.videoChange(customerVideoData);
    sessionTags = null;
  }

  /**
   * This is called by the application layer when different content has been played on the same URL,
   * for example during a live stream. This method will close the existing view and start a new one.
   *
   * @param customerVideoData new video data for new video view.
   */
  @SuppressWarnings("unused")
  public void programChange(CustomerVideoData customerVideoData) {
    resetInternalStats();
    muxStats.programChange(customerVideoData);
  }

  /**
   * Called when the device orientation is changed, called by the Application layer.
   *
   * @param orientation new device orientation.
   */
  public void orientationChange(MuxSDKViewOrientation orientation) {
    muxStats.orientationChange(orientation);
  }

  /**
   * Called when the video changes from being presented fullscreen or normal.
   *
   * If this is not called, the SDK will attempt to guess the presentation by comparing the size of
   * the player view to the size of the screen. This works in many use cases, but if your fullscreen
   * player view's dimensions may not match the dimensions of the {@link android.view.Display},
   * consider manually setting the presentation to detect fullscreen playback events.
   *
   * @param presentation new presentation, or null for auto-detection (see above)
   */
  public void presentationChange(MuxSDKViewPresentation presentation) {
    muxStats.presentationChange(presentation);
  }

  /**
   * Set player render surface.
   *
   * @param playerView player render surface.
   */
  public void setPlayerView(View playerView) {
    this.playerView = new WeakReference<>(playerView);
  }

  /**
   * Set player size in physical pixels.
   *
   * @param width in physical pixels.
   * @param height in physical pixels.
   */
  @SuppressWarnings("unused")
  public void setPlayerSize(int width, int height) {
    muxStats.setPlayerSize(width, height);
  }

  /**
   * Convert the actual number of pixels to density pixels and set in the Core SDK.
   *
   * @param width screen width in pixels.
   * @param height screen height in pixels.
   */
  public void setScreenSize(int width, int height) {
    muxStats.setScreenSize(pxToDp(width), pxToDp(height));
  }

  /**
   * Report an exception to the backend.
   *
   * @param e exception to be reported.
   */
  public void error(MuxErrorException e) {
    muxStats.error(e);
  }

  @SuppressWarnings("unused")
  public void setAutomaticErrorTracking(boolean enabled) {
    muxStats.setAutomaticErrorTracking(enabled);
  }

  /**
   * Release the underlying SDK. This will generate some additional events such as
   * {@link com.mux.stats.sdk.core.events.playback.ViewEndEvent}.
   */
  public void release() {
    if (updatePlayheadPositionTimer != null) {
      updatePlayheadPositionTimer.cancel();
    }
    muxStats.release();
    muxStats = null;
    player = null;
  }

  /**
   * This was used to tell us if we are playing HLS or DASH because this can not be determined
   * automatically.
   *
   * @param type stream type.
   */
  @Deprecated
  @SuppressWarnings("unused")
  public void setStreamType(int type) {
    streamType = type;
  }

  /**
   * Dispatch the new event and increment appropriate counters.
   *
   * @param event
   */
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

  /**
   * Return the current playback position.
   *
   * @return playback position in milliseconds.
   */
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

  /**
   * Return the internal playback state defined here: {@link PlayerState}, this
   * can be sometimes different then {@link ExoPlayer} internal state.
   */
  // State Transitions
  public PlayerState getState() {
    return state;
  }

  /**
   * Detect if the current media item being played contains a video track.
   */
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

  /**
   * Start a periodic timer which will update the playback position on every 150 ms.
   */
  protected void setPlaybackHeadUpdateInterval() {
    if (updatePlayheadPositionTimer != null) {
      updatePlayheadPositionTimer.cancel();
    }
    // Schedule timer to execute, this is for audio only content.
    updatePlayheadPositionTimer = new Timer();
    updatePlayheadPositionTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        if (state == PlayerState.PLAYING
            || state == PlayerState.PLAYING_ADS
            || state == PlayerState.SEEKING ) {
          playerHandler.obtainMessage(ExoPlayerHandler.UPDATE_PLAYER_CURRENT_POSITION).sendToTarget();
        }
      }
    }, 0, 150);
  }

  /**
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

  /**
   * Recalculate the player width in density pixels, this can vary on different displays.
   *
   * @return player width in density pixels.
   */
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

  /**
   * Recalculate the physical player height to density pixels, this can vary on different displays.
   *
   * @return player height in density pixels.
   */
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

  /**
   * This is the time of the current playback position as extrapolated from the PDT tags in the
   * stream. Only available for DASH and HLS.
   *
   * @return time.
   */
  @Override
  public Long getPlayerProgramTime() {
    if (currentTimelineWindow != null && playerHandler != null) {
      return currentTimelineWindow.windowStartTimeMs + playerHandler.getPlayerCurrentPosition();
    }
    return -1L;
  }

  /**
   * This is the time of the furthest position in the manifest as extrapolated from the PDT tags in
   * the stream. Only available for DASH and HLS.
   *
   * @return time.
   */
  @Override
  public Long getPlayerManifestNewestTime() {
    if (currentTimelineWindow != null && isLivePlayback()) {
      return currentTimelineWindow.windowStartTimeMs;
    }
    return -1L;
  }

  /**
   * The configured holdback value for a live stream (ms). Analagous to the HOLD-BACK manifest tag.
   * Only available for DASH and HLS.
   *
   * @return value in milliseconds.
   */
  @Override
  public Long getVideoHoldback() {
    return isLivePlayback()? parseHlsManifestTagLong("HOLD-BACK") : null;
  }

  /**
   * The configured holdback value for parts in a low latency live stream (ms). Analagous to the
   * PART-HOLD-BACK manfiest tag. Only available for DASH and HLS.
   *
   * @return value in milliseconds.
   */
  @Override
  public Long getVideoPartHoldback() {
    return isLivePlayback()? parseHlsManifestTagLong("PART-HOLD-BACK") : null;
  }

  /**
   * The configured target duration for parts in a low-latency live stream (ms). Analogous to the
   * PART-TARGET attribute within the EXT-X-PART-INF manifest tag. Only available for DASH and HLS.
   *
   * @return value in milliseconds.
   */
  @Override
  public Long getVideoPartTargetDuration() {
    return isLivePlayback()? parseHlsManifestTagLong("PART-TARGET") : null;
  }

  /**
   *  The configured target duration for segments in a live stream (ms). Analogous to the
   *  EXT-X-TARGETDURATION manifest tag. Only available for DASH and HLS.
   *
   * @return value in milliseconds.
   */
  @Override
  public Long getVideoTargetDuration() {
    return isLivePlayback()? parseHlsManifestTagLong("EXT-X-TARGETDURATION") : null;
  }

  /**
   * Determine if the current player state is paused.
   *
   * @return true if playback is paused.
   */
  @Override
  public boolean isPaused() {
    return state == PlayerState.PAUSED || state == PlayerState.ENDED || state == PlayerState.ERROR
        || state == PlayerState.INIT;
  }

  /**
   * Called whenever {@link ExoPlayer} reports that the player is buffering.
   * Checks the player internal playback state {@link PlayerState} and determines
   * if a new {@link TimeUpdateEvent} should be dispatched and the internal {@link #state} set to
   * {@link PlayerState#BUFFERING}.
   */
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

  /**
   * Called when {@link ExoPlayer} reports that the player is paused.
   * Checks the player internal playback state {@link #state} and determines
   * if {@link #rebufferingEnded()} should be called when rebuffering is in progress,
   * or {@link #seeked(boolean)} should be called when seeking is in process,
   * or this is just a clean pause event.
   */
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

  /**
   * Called when {@link ExoPlayer} reports playback started.
   * Checks if {@link PlayEvent} should be sent or ignored depending on the current player state.
   */
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

  /**
   * Called when {@link ExoPlayer} triggers a playing event, this function will decide if there is
   * a missing {@link PauseEvent} event that was supposed to precede the current
   * {@link PlayingEvent} and if it is not present then the {@link PauseEvent} will be dispatched
   * first followed by the {@link PlayingEvent}.
   *
   * If the player is in the rebuffering state and the rebuffer end event was not received then the
   * {@link #rebufferingEnded()} function will be called first.
   *
   * All events of this type are ignored if {@link #seekingInProgress} is set to true.
   *
   * As a final step the function will dispatch the {@link PlayingEvent} and set {@link #state} to
   * {@link PlayerState#PLAYING}
   */
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

  /**
   * Called when rebuffering is triggered. Rebuffering is a buffer event triggered while
   * {@link #state} is equal to {@link PlayerState#PLAYING}. Any other value would be
   * considered Buffering and the {@link #buffering()} function would have been called.
   */
  protected void rebufferingStarted() {
    state = PlayerState.REBUFFERING;
    dispatch(new RebufferStartEvent(null));
  }

  /**
   * Called when {@link ExoPlayer} reports a playing state after
   * {@link #rebufferingStarted()} was called.
   */
  protected void rebufferingEnded() {
    dispatch(new RebufferEndEvent(null));
  }

  /**
   * Called when {@link ExoPlayer} reports a playback discontinuity or seek start event.
   * If called while {@link #state} is equal to {@link PlayerState#PLAYING} then {@link PauseEvent}
   * will be dispatched to precede the following {@link SeekingEvent}.
   *
   * This method will set {@link #seekingInProgress} to true.
   */
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

  /**
   * Called when the end of playback has been reached.
   */
  protected void ended() {
    dispatch(new PauseEvent(null));
    dispatch(new EndedEvent(null));
    state = PlayerState.ENDED;
  }

  /**
   * Dispatch an internal SDK error to the backend.
   *
   * @param error to be reported.
   */
  protected void internalError(Exception error) {
    if (error instanceof MuxErrorException) {
      MuxErrorException muxError = (MuxErrorException) error;
      dispatch(new InternalErrorEvent(muxError.getCode(), muxError.getMessage()));
    } else {
      dispatch(new InternalErrorEvent(ERROR_UNKNOWN,
          error.getClass().getCanonicalName() + " - " + error.getMessage()));
    }
  }

  /**
   * Called when adaptive bitstream quality has been changed either due to changes in
   * the internet connection or as the result of an explicit request by the Application. This only
   * applies to the DASH and HLS streams.
   *
   * @param format new bitstream quality format.
   */
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

  /**
   * Reset internal counters for each new view.
   */
  private void resetInternalStats() {
    detectMimeType = true;
    numberOfPauseEventsSent = 0;
    numberOfPlayEventsSent = 0;
    numberOfEventsSent = 0;
    firstFrameReceived = false;
    firstFrameRenderedAt = -1;
    currentTimelineWindow = new Window();
  }

  /**
   * See {{@link #parseHlsManifestTag(String)}}, parse the tag value as a Long value.
   * @param tagName tag name to parse
   * @return Long value of the tag if possible, -1 otherwise.
   */
  private Long parseHlsManifestTagLong(String tagName) {
    String value = parseHlsManifestTag(tagName);
    value = value.replace(".", "");
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      MuxLogger.d(TAG, "Bad number format for value: " + value);
    }
    return -1L;
  }

  /**
   * Communication handler. Receive messages from other threads and execute on the main thread.
   */
  static class ExoPlayerHandler extends Handler {

    static final int UPDATE_PLAYER_CURRENT_POSITION = 1;

    /** Used to internally track the current playback position. */
    AtomicLong playerCurrentPosition = new AtomicLong(0);
    /** Reference to overlaying SDK interface. */
    MuxBaseExoPlayer muxStats;

    /**
     * Basic constructor.
     *
     * @param looper usually the main thread looper.
     * @param muxStats Reference to overlaying SDK interface.
     */
    public ExoPlayerHandler(Looper looper, MuxBaseExoPlayer muxStats) {
      super(looper);
      this.muxStats = muxStats;
    }

    /**
     * Return current player playback position.
     *
     * @return milliseconds on playback time.
     */
    public long getPlayerCurrentPosition() {
      return playerCurrentPosition.get();
    }

    /**
     * Process messages sent by other threads.
     *
     * @param msg message to be processed.
     */
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

  /**
   * Basic device details such as OS version, vendor name and etc. Instances of this class
   * are used by {@link MuxStats} to interface with the device.
   */
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
    /**
     * Use this value instead of auto detected name in case the value is different then null.
     */
    protected String metadataDeviceName = null;

    /**
     * Basic constructor.
     *
     * @param ctx activity context, we use this to access different system services, like
     *           {@link ConnectivityManager}, or {@link PackageInfo}.
     */
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

    public void overwriteDeviceMetadata(String deviceName) {
      metadataDeviceName = deviceName;
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
      if (metadataDeviceName != null) {
        return metadataDeviceName;
      }
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

    /**
     * Determine the correct network connection type.
     *
     * @return the connection type name.
     */
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
    public void outputLog(LogPriority logPriority, String tag, String msg) {
      switch (logPriority) {
        case ERROR:
          Log.e(tag, msg);
          break;
        case WARN:
          Log.w(tag, msg);
          break;
        case INFO:
          Log.i(tag, msg);
          break;
        case DEBUG:
          Log.d(tag, msg);
          break;
        case VERBOSE:
        default: // fall-through
          Log.v(tag, msg);
          break;
      }
    }

    /**
     * Print underlying {@link MuxStats} SDK messages on the logcat. This will only be
     * called if {@link #enableMuxCoreDebug(boolean, boolean)} is called with first argument as true
     *
     * @param tag tag to be used.
     * @param msg message to be printed.
     */
    @Override
    public void outputLog(String tag, String msg) {
      Log.v(tag, msg);
    }
  }

  /**
   * Calculate Bandwidth metrics of an HLS or DASH segment. {@link ExoPlayer} will trigger
   * these events in {@link MuxStatsExoPlayer} and will be propagated here for processing, at this
   * point both HLS and DASH segments are processed in same way so all metrics are collected here.
   */
  class BandwidthMetric {
    /** Available qualities. */
    TrackGroupArray availableTracks;
    /**
     * Each segment that started loading is stored here until the segment ceases loading.
     * The segment url is the key value of the map.
     */
    HashMap<String, BandwidthMetricData> loadedSegments = new HashMap<>();

    /**
     * When the segment failed to load an error will be reported to the backend. This also
     * removes the segment that failed to load from the {@link #loadedSegments} hash map.
     *
     * @param segmentUrl, url of the segment that failed to load.
     * @param e, error that occured.
     * @return segment that failed to load.
     */
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

    /**
     * If the segment is no longer needed this function will be triggered. This can happen if
     * the player stopped the playback and wants to stop all network loading. In that case we will
     * remove the appropriate segment from {@link #loadedSegments}.
     *
     * @param segmentUrl, url of the segment that wants to be loaded.
     * @return Canceled segment.
     */
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
      // Populate segment time details.
      if (player != null && player.get() != null) {
        synchronized (currentTimelineWindow) {
          try {
            player.get().getCurrentTimeline()
                .getWindow(player.get().getCurrentWindowIndex(), currentTimelineWindow);
          } catch (Exception e) {
            // Failed to obtrain data, ignore, we will get it on next call
          }
        }
      }
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

    /**
     * Called when a segment starts loading. Set appropriate metrics and store the segment in
     * {@link #loadedSegments}. It will be then sent to the backend once the appropriate one of
     * {@link #onLoadCompleted(String, long, Format)} ,{@link #onLoadError(String, IOException)} or
     * {@link #onLoadCanceled(String)} is called for this segment.
     *
     * @param mediaStartTimeMs, {@link ExoPlayer} reported segment playback start time, this refer
     *                                           to playback position of segment inside the media
     *                                           presentation (DASH or HLS stream).
     * @param mediaEndTimeMs, {@link ExoPlayer} reported playback end time, this refer to playback
     *                                         position of segment inside the media presentation
     *                                         (DASH or HLS stream).
     * @param segmentUrl, url of the segment that is being loaded, used as a unique id for segment
     *                    storage in {@link #loadedSegments} table.
     * @param dataType, type of the segment (manifest, media etc ...)
     * @param host, host associated with this segment.
     * @return new segment.
     */
    public BandwidthMetricData onLoadStarted(long mediaStartTimeMs, long mediaEndTimeMs,
        String segmentUrl, int dataType, String host, String segmentMimeType) {
      BandwidthMetricData loadData = onLoad(mediaStartTimeMs, mediaEndTimeMs, segmentUrl
          , dataType, host, segmentMimeType);
      if (loadData != null) {
        loadData.setRequestResponseStart(System.currentTimeMillis());
      }
      return loadData;
    }

    /**
     * Called when segment is loaded. This function will retrieve the statistics for this segment
     * from {@link #loadedSegments} and fill out the remaining metrics.
     *
     * @param segmentUrl url related to the segment.
     * @param bytesLoaded number of bytes needed to load the segment.
     * @param trackFormat Media details related to the segment.
     * @return loaded segment.
     */
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
      loadData.setRequestCancel("FragLoadEmergencyAborted");
      return loadData;
    }

    @Override
    public BandwidthMetricData onLoadCompleted(String segmentUrl,
        long bytesLoaded,
        Format trackFormat) {
      BandwidthMetricData loadData = super.onLoadCompleted(segmentUrl, bytesLoaded, trackFormat);
      if (trackFormat != null && loadData != null) {
        MuxLogger.d(TAG, "\n\nWe got new rendition quality: " + trackFormat.bitrate + "\n\n");
        loadData.setRequestLabeledBitrate(trackFormat.bitrate);
      }
      return loadData;
    }
  }

  /**
   * Determine which stream is being parsed (HLS or DASH) and then use an appropriate
   * {@link BandwidthMetric} to parse the stream. The problem with this is that it is not possible
   * to reliably detect the stream type currently being played so this part is not functional.
   * Luckily logic for HLS parsing is same as logic for DASH parsing so for both streams we use
   * {@link BandwidthMetricHls}.
   */
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

  /**
   * Convert physical pixels to device density independent pixels.
   *
   * @param px physical pixels to be converted.
   * @return number of density pixels calculated.
   */
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

  /** HLS and DASH segment metric parser. */
  protected BandwidthMetricDispatcher bandwidthDispatcher = new BandwidthMetricDispatcher();
  /** List of available qualities in DASH or HLS stream, currently not used. */
  protected List<BandwidthMetricData.Rendition> renditionList;
}
