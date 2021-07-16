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

/**
 * This class connects the {@link ExoPlayer}, {@link MuxStats} and
 * {@link com.google.ads.interactivemedia.v3.api}.
 *
 * Actual hook to data statistic for {@link ExoPlayer} is implemented here
 * {@link MuxStatsExoPlayer}, this class is a basic logic for event processing that is independent
 * of {@link ExoPlayer} version and make sure that all events are passed to {@link MuxStats} in
 * correct order and with appropriate statistics.
 */
public class MuxBaseExoPlayer extends EventBus implements IPlayerListener {

  protected static final String TAG = "MuxStatsListener";
  /** Error codes start at -1 as ExoPlaybackException codes start at 0 and go up. */
  protected static final int ERROR_UNKNOWN = -1;
  protected static final int ERROR_DRM = -2;
  protected static final int ERROR_IO = -3;

  /**
   * This is the number of video frames that need to be rendered after the player have seeked in
   * order for us to conclude that playback have actually started, we use this value in a workaround
   * to detect playing event when {@link SeekedEvent} event is some times reported by
   * {@link ExoPlayer} few milliseconds after after {@link PlayingEvent} which is not what is
   * expected.
   * */
  protected static final int NUMBER_OF_FRAMES_THAT_ARE_CONSIDERED_PLAYBACK = 2;

  /**
   * This variable store the mime type of current playback container, in case of dash and HLS the
   * playback container is not possible to detect because {@link ExoPlayer} report mime type of
   * segment container, in this case we do not set this value and the video mime type is calculated
   * on the backend based on the url.
   */
  protected String mimeType;
  /** This is the width of current playback video resolution. */
  protected Integer sourceWidth;
  /** This is the height of current playback video resolution. */
  protected Integer sourceHeight;
  /**
   * This is the bitrate declared in playback container, this can be different from the actual
   * calculated bitrate.
   */
  protected Integer sourceAdvertisedBitrate;
  /**
   * This is the framerate declared in video container, it can be different from the actual
   * frameratre
   */
  protected Float sourceAdvertisedFramerate;
  /**
   * This is the duration of current media segment in milliseconds, this value is extracted from the
   * Media container and can also be different from actual Media duration.
   */
  protected Long sourceDuration;
  /** This is used to update the current playback position in real time. */
  protected ExoPlayerHandler playerHandler;
  /**
   * This is used to get notification for each new frame being rendered on the player surface,
   * we use this to then notify the {@link #playerHandler} to update the playback position, this is
   * important because this listener post notification in different thread which should not be
   * blocked so we then just push the new message to {@link #playerHandler} queue.
   */
  protected FrameRenderedListener frameRenderedListener;
  /**
   * This is used to update the current playback position in case the current media does not have
   * video component, in that case we can not use {@link #frameRenderedListener} because no frames
   * are being rendered, update is being done by sending a message to the {@link #playerHandler}.
   */
  protected Timer updatePlayheadPositionTimer;

  /** Here we store the {@link ExoPlayer} instance. */
  protected WeakReference<ExoPlayer> player;
  /** This is the UI object that contain the render surface. */
  protected WeakReference<View> playerView;
  /** Activity context. */
  protected WeakReference<Context> contextRef;
  /** Ad monitoring and processing logic. */
  protected AdsImaSDKListener adsImaSdkListener;

  /** Event counter, this is useful to know when the view have started. */
  protected int numberOfEventsSent = 0;
  /** Number of {@link PlayingEvent} sent since the View started. */
  protected int numberOfPlayEventsSent = 0;
  /** Number of {@link PauseEvent} sent since the View started. */
  protected int numberOfPauseEventsSent = 0;
  /**
   * This was used to determine if we are playing HLS or DASH, it is not possible to automatically
   * detect this so we exopesed the {@link #setStreamType(int)} method and we expect an application
   * layer to set this value properly.
   * At this point we make no difference in data collection between HLS and DASH so this value is
   * not needed.
   */
  @Deprecated
  protected int streamType = -1;

  /**
   * These are the different playback states that are monitored internally, {@link ExoPlayer} keep
   * it's own internal state which sometimes can be different then one described here.
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
   * Set to true if the player is currently seeking, this also include all necessary network
   * buffering to start the playback from new position.
   */
  boolean seekingInProgress;
  /**
   * Frame counter, we increment this value on every new video frame rendered and we reset this
   * counter to zero on new View and once the seeking have started.
   */
  int numberOfFramesRenderedSinceSeekingStarted;
  /** This value is set to true if {@link com.google.android.exoplayer2.MediaItem} have video. */
  boolean playItemHaveVideoTrack;


  /**
   * Basic constructor.
   *
   * @param ctx Activity context.
   * @param player ExoPlayer to monitor, must be unique per {@link MuxBaseExoPlayer} instance, two
   *               different instances can not monitor same player at same time, as soon as second
   *               instance is created the first instance will stop receiving events from the player
   * @param playerName this is unique player id, it is static field, each instance must have unique
   *                   player name in same process.
   * @param customerPlayerData basic playback data set by the Application.
   * @param customerVideoData basic Video data set by the Application.
   * @param customerViewData basic View data set by the application.
   * @param sentryEnabled if set to true the underlying {@link MuxStats} will report internal errors
   *                      to backend.
   * @param networkRequest internet interface implementation.
   */
  MuxBaseExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData, CustomerVideoData customerVideoData,
      CustomerViewData customerViewData, boolean sentryEnabled,
      INetworkRequest networkRequest) {
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

  /**
   * Allow HTTP header with a given name to be passed to the backend, by default we ignore all HTTP
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

  /**
   * Update the Application set data. If any of the given arguments is null it will not override the
   * existing data.
   *
   * @param customPlayerData new data to be used.
   * @param customVideoData new data to be used.
   */
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

  /**
   * If set to true the underlying {@link MuxStats} logs will be printend in the logcat.
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
  }

  /**
   * This is called by the application layer when different content have been played on same URL
   * which can be live stream for example. This method will close existing view and start a new one.
   *
   * @param customerVideoData new video data for new video view.
   */
  @SuppressWarnings("unused")
  public void programChange(CustomerVideoData customerVideoData) {
    resetInternalStats();
    muxStats.programChange(customerVideoData);
  }

  /**
   * Called when device orientation is changed, called by the Application layer.
   *
   * @param orientation new device orientation.
   */
  public void orientationChange(MuxSDKViewOrientation orientation) {
    muxStats.orientationChange(orientation);
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
   * Set screen size in physical pixels.
   *
   * @param width in physical pixels.
   * @param height in physical pixels.
   */
  public void setScreenSize(int width, int height) {
    muxStats.setScreenSize(width, height);
  }

  /**
   * Report exception to the backend.
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
   * Release the underlying SDK. This will generate some additional events like
   * {@link com.mux.stats.sdk.core.events.playback.ViewEndEvent}.
   */
  public void release() {
    muxStats.release();
    muxStats = null;
    player = null;
  }

  /**
   * This was used to tell us if we are playing HLS or DASH, because this can not be determined
   * Automatically.
   *
   * @param type stream type.
   */
  @Deprecated
  @SuppressWarnings("unused")
  public void setStreamType(int type) {
    streamType = type;
  }

  /**
   * Dispatch new event and increment appropriate counters.
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
   * State Transitions, return the internal playback state defined here: {@link PlayerState}, this
   * can be sometimes different then {@link ExoPlayer} internal state.
   */
  public PlayerState getState() {
    return state;
  }

  /**
   * This method will detect if the current media item being played contains video track, if it does
   * it will initialize the {@link #frameRenderedListener} or if it does not then it will initialize
   * {@link #updatePlayheadPositionTimer}.
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
   * Determine if current player state is paused.
   *
   * @return true if playback is paused.
   */
  @Override
  public boolean isPaused() {
    return state == PlayerState.PAUSED || state == PlayerState.ENDED || state == PlayerState.ERROR
        || state == PlayerState.INIT;
  }

  /**
   * Whenever {@link ExoPlayer} report that player is buffering this function will be called.
   * Function will check the player internal playback state {@link PlayerState} and determine
   * if new {@link TimeUpdateEvent} should be dispatched and internal {@link #state} be set to
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
   * Whenever {@link ExoPlayer} report that player is paused this function will be called.
   * Function will check the player internal playback state {@link #state} and determine
   * if {@link #rebufferingEnded()} should be called in case that rebuffering is in process.
   * or if {@link #seeked(boolean)} should be called in case that seeking is in process,
   * or if this is just an clean pause event.
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
   * Whenever {@link ExoPlayer} report play started this function will be called. Function will
   * check if {@link PlayEvent} should be sent or ignored depending on the current player state.
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
   * Called when {@link ExoPlayer} trigger playing event, this function will decide if there is
   * missing {@link PauseEvent} event that suppose to precede current {@link PlayingEvent} and if it
   * is not present then {@link PauseEvent} will be dispatched first and then {@link PlayingEvent}.
   *
   * If player is in rebuffering state and rebuffer end event was not received then
   * {@link #rebufferingEnded()} function will be called first.
   *
   * All events of this type are ignored if {@link #seekingInProgress} is set to true.
   *
   * As a final step the function will dispatch {@link PlayingEvent} and set {@link #state} to
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
   * Called when rebuffering is triggered, rebuffering is an buffer event triggered while
   * {@link #state} is equal to {@link PlayerState#PLAYING}, on any other value this would be
   * considered Buffering and {@link #buffering()} function would have been called.
   */
  protected void rebufferingStarted() {
    state = PlayerState.REBUFFERING;
    dispatch(new RebufferStartEvent(null));
  }

  /**
   * This method is called when {@link ExoPlayer} report playing state after
   * {@link #rebufferingStarted()} was called.
   */
  protected void rebufferingEnded() {
    dispatch(new RebufferEndEvent(null));
  }

  /**
   * Called when {@link ExoPlayer} report playback discontinuity or report seek start event.
   * If called while {@link #state} is equal to {@link PlayerState#PLAYING} then {@link PauseEvent}
   * will be dispatched to precede following {@link SeekingEvent}.
   *
   * This method will set {@link #seekingInProgress} to true.
   */
  protected void seeking() {
    if (state == PlayerState.PLAYING) {
      dispatch(new PauseEvent(null));
    }
    state = PlayerState.SEEKING;
    seekingInProgress = true;
    numberOfFramesRenderedSinceSeekingStarted = 0;
    dispatch(new SeekingEvent(null));
  }

  /**
   * Seeked event will be fired by the player immediately after seeking event
   * This is not accurate, instead report the seeked event on first few frames rendered.
   * This function is called each time a new frame is about to be rendered.
   *
   * This function is called after {@link #NUMBER_OF_FRAMES_THAT_ARE_CONSIDERED_PLAYBACK} are
   * registered in {@link #frameRenderedListener} which is considered to be playback, in that case
   * we conclude that playback have been resumed and we can officially say that seeking process have
   * been terminated. We use this approach because {@link ExoPlayer} can sometimes report fake
   * buffering or rebuffering events that are not representing the reality, so in this way we are
   * trying to ignore those false events. For audio only
   * {@link com.google.android.exoplayer2.MediaItem} we do not have this fake rebuffering events.
   *
   * @param newFrameRendered is set to true when current playing
   * {@link com.google.android.exoplayer2.MediaItem} contains Video track, it will be false
   *                         otherwise. Value of this argument determine if we conclude that seek
   *                         have ended based on the number of frames rendered since seek started,
   *                         or we just call seek ended when {@link ExoPlayer} report seek ended.
   */
  protected void seeked(boolean newFrameRendered) {
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

  /**
   * Called when end of playback have been reached.
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
   * This function will be executed when adaptive bitstream quality have been changed either due
   * to the changes in internet connection or on explicit request of the Application. This only
   * apply to the DASH and HLS streams.
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
    numberOfPauseEventsSent = 0;
    numberOfPlayEventsSent = 0;
    numberOfEventsSent = 0;
  }

  /**
   * Frame listener, this class is used to capture each new frame that is about to be rendered.
   */
  static class FrameRenderedListener implements VideoFrameMetadataListener {

    /** Used to communicate with the player thread */
    ExoPlayerHandler handler;

    /**
     * Basic constructor.
     *
     * @param handler to communicate with the main thread.
     */
    public FrameRenderedListener(ExoPlayerHandler handler) {
      this.handler = handler;
    }

    /**
     * As of r2.11.x, the signature for this callback has changed. These are not annotated as
     * @Overrides in order to support both before r2.11.x and after r2.11.x at the same time.
     *
     * @param presentationTimeUs UTC timestamp on when frame is being rendered.
     * @param releaseTimeNs not sure about this.
     * @param format frame quality format.
     */
    public void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs,
        Format format) {
      handler.obtainMessage(ExoPlayerHandler.UPDATE_PLAYER_CURRENT_POSITION).sendToTarget();
    }

    /**
     * As of r2.11.x, the signature for this callback has changed. These are not annotated as
     * @Overrides in order to support both before r2.11.x and after r2.11.x at the same time.
     *
     * @param presentationTimeUs UTC timestamp on when frame is being rendered.
     * @param releaseTimeNs not sure about this.
     * @param format frame quality format.
     * @param mediaFormat not sure about this.
     */
    public void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs,
        Format format, @Nullable MediaFormat mediaFormat) {
      handler.obtainMessage(ExoPlayerHandler.UPDATE_PLAYER_CURRENT_POSITION).sendToTarget();
    }
  }

  /**
   * Communication handler, receive messages from other threads and execute on main thread.
   */
  static class ExoPlayerHandler extends Handler {

    static final int UPDATE_PLAYER_CURRENT_POSITION = 1;

    /** Used to internally track current playback position. */
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
          muxStats.seeked(true);
          break;
        default:
          MuxLogger.d(TAG, "ExoPlayerHandler>> Unhandled message type: " + msg.what);
      }
    }
  }

  /**
   * Implement basic device details such as OS version, vendor name and etc. Instance of this class
   * is used in underlying {@link MuxStats} interface to obtain basic hardware details.
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
     * Basic constructor.
     *
     * @param ctx activity context.
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

    /**
     * Used to print underlying {@link MuxStats} SDK messages on the logcat. This will only be
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
   * Used to callculate Bandwidth metrics of HLS or DASH segment.
   */
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
        String segmentUrl, int dataType, String host
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
          segmentData.setRequestType("manifest");
          break;
        case C.DATA_TYPE_MEDIA:
          segmentData.setRequestType("media");
          break;
        default:
          return null;
      }
      segmentData.setRequestResponseHeaders(null);
      segmentData.setRequestHostName(host);
      if (dataType == C.DATA_TYPE_MEDIA) {
        segmentData.setRequestMediaDuration(mediaEndTimeMs
            - mediaStartTimeMs);
      }
      segmentData.setRequestRenditionLists(renditionList);
      loadedSegments.put(segmentUrl, segmentData);
      return segmentData;
    }

    public BandwidthMetricData onLoadStarted(long mediaStartTimeMs, long mediaEndTimeMs,
        String segmentUrl, int dataType, String host) {
      BandwidthMetricData loadData = onLoad(mediaStartTimeMs, mediaEndTimeMs, segmentUrl
          , dataType, host);
      if (loadData != null) {
        loadData.setRequestResponseStart(System.currentTimeMillis());
      }
      return loadData;
    }

    public BandwidthMetricData onLoadCompleted(String segmentUrl, long bytesLoaded,
        Format trackFormat) {
      BandwidthMetricData segmentData = loadedSegments.get(segmentUrl);
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
      if (trackFormat != null) {
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
        int dataType, String host) {
      if (player == null || player.get() == null || muxStats == null
          || currentBandwidthMetric() == null) {
        return;
      }
      currentBandwidthMetric().onLoadStarted(mediaStartTimeMs, mediaEndTimeMs, segmentUrl
          , dataType, host);
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
      parseHeaders(loadData, responseHeaders);
      dispatch(loadData, new RequestCompleted(null));
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
