package com.mux.stats.sdk.muxstats

import android.util.Log
import com.google.android.exoplayer2.Timeline
import com.mux.stats.sdk.core.events.IEvent
import com.mux.stats.sdk.core.events.IEventDispatcher
import com.mux.stats.sdk.core.events.InternalErrorEvent
import com.mux.stats.sdk.core.events.playback.*
import com.mux.stats.sdk.core.model.BandwidthMetricData
import com.mux.stats.sdk.core.model.CustomerVideoData
import com.mux.stats.sdk.core.model.SessionTag
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.BandwidthMetricDispatcher
import com.mux.stats.sdk.muxstats.internal.logTag
import com.mux.stats.sdk.muxstats.internal.noneOf
import com.mux.stats.sdk.muxstats.internal.oneOf
import kotlinx.coroutines.*
import java.util.*
import kotlin.properties.Delegates

/**
 * Collects events from a player and delivers them into a MuxStats instance.
 * As a player's state model may differ from that used by Mux Data products, the state as understood
 * by Mux Data is tracked here.
 *
 * You should supply one of these to [MuxPlayerAdapter], and call is methods from your
 * [MuxPlayerAdapter.PlayerBinding]
 */
abstract class MuxStateCollectorBase(
  // TODO em: if MuxStateCollector is in MuxStats, don't need this janky block
  val muxStats: () -> MuxStats,
  val dispatcher: IEventDispatcher,
  val trackFirstFrameRendered: Boolean = true,
) {

  companion object {
    const val TIME_UNKNOWN = -1L
    const val ERROR_UNKNOWN = -1;
    const val ERROR_DRM = -2
    const val ERROR_IO = -3

    private const val FIRST_FRAME_NOT_RENDERED: Long = -1

    // Wait this long after the first frame was rendered before logic considers it rendered
    private const val FIRST_FRAME_WAIT_MILLIS = 50L
  }

  /**
   * This is the time to wait in ms that needs to pass after the player has seeked in
   * order for us to conclude that playback has actually started. We use this value in a workaround
   * to detect the playing event when {@link SeekedEvent} event is some times reported by
   * {@link ExoPlayer} a few milliseconds after the {@link PlayingEvent} which is not what is
   * expected.
   * */
  var timeToWaitAfterFirstFrameReceived: Long = 50

  /**
   * The current state of the player, as represented by Mux Data
   */
  val muxPlayerState by ::_playerState
  private var _playerState = MuxPlayerState.INIT

  /** Event counter. This is useful to know when the view have started.  */
  var detectMimeType = true

  /**
   * Detected MIME type of the playing media, if applicable
   */
  var mimeType: String? = null

  /**
   * True if the media being played has a video stream, false if not
   * This is used to decide how to handle position discontinuities for audio-only streams
   * The default value is true, which might be fine to keep, depending on your player
   */
  var mediaHasVideoTrack: Boolean? = true

  /** Store all time detailes of current segment  */
  var currentTimelineWindow: Timeline.Window = Timeline.Window()

  /**
   * Total duration of the media being played, in milliseconds
   */
  var sourceDurationMs: Long = TIME_UNKNOWN

  /**
   * The current playback position of the player
   */
  var playbackPositionMills: Long = TIME_UNKNOWN

  /**
   * The media bitrate advertised by the current media item
   */
  var sourceAdvertisedBitrate: Int = 0

  /**
   * The frame rate advertised by the current media item
   */
  var sourceAdvertisedFrameRate: Float = 0F

  /**
   * Width of the current media item in pixels. 0 for non-video media
   */
  var sourceWidth: Int = 0

  /**
   * Width of the current media item in pixels. 0 for non-video media
   */
  var sourceHeight: Int = 0

  /**
   * An asynchronous watcher for playback position. It waits for the given update interval, and
   * then sets the [.playbackPositionMillis] property on this object. It can be stopped by
   * calling [PositionWatcher.stop], and will automatically stop if it can no longer
   * access play time info
   */
  var positionWatcher: PositionWatcher?
          by Delegates.observable(null) @Synchronized { _, old, new ->
            old?.apply { stop("watcher replaced") }
          }

  private var sessionTags: List<SessionTag> = Collections.emptyList()

  private var firstFrameRenderedAtMillis = FIRST_FRAME_NOT_RENDERED // Based on system time
  private var seekingInProgress = false // TODO: em - We have a SEEKING state so why do we have this
  private var firstFrameReceived = false

  private var pauseEventsSent = 0
  private var playEventsSent = 0
  private var totalEventsSent = 0
  private var seekingEventsSent = 0
  private var seekedEventsSent = 0

  private var dead = false

  /** List of available qualities in DASH or HLS stream, currently not used.  */
  var renditionList: List<BandwidthMetricData.Rendition>? = null

  /**
   *  List of string patterns that will be used to determine if certain HTTP header will be
   *  reported to the backend.
   *  */
  var allowedHeaders = ArrayList<String>()


  /**
   * Return true if DASH or HLS content being played is live.
   */
  abstract fun isLivePlayback(): Boolean

  /**
   * Extracts the tag value from live HLS/dash segment, returns -1 if the header ios not present.
   *
   * @param tagName name of the tag to extract from the manifest.
   * @return tag value if tag is found and we are playing live stream, -1 string otherwise.
   */
  abstract fun parseManifestTag(tagName: String): String

  fun parseManifestTagL(tagName: String): Long {
    var value: String = parseManifestTag(tagName)
    value = value.replace(".", "")
    try {
      return value.toLong()
    } catch (e: NumberFormatException) {
      Log.d("Manifest Parsing", "Bad number format for value: $value")
    }
    return -1L
  }

  /**
   * Allow HTTP headers with a given name to be passed to the backend. By default we ignore all HTTP
   * headers that are not in the [BandwidthMetricDispatcher.allowedHeaders] list.
   * This is used in automated tests and is not intended to be used from the application layer.
   *
   * @param headerName name of the header to send to the backend.
   */
  fun allowHeaderToBeSentToBackend(headerName: String?) {
    if (headerName != null) {
      allowedHeaders.add(headerName)
    }
  }

  /**
   * Call when the player starts buffering. Buffering events after the player began playing are
   * reported as rebuffering events
   */
  fun buffering() {
    // Only process buffering if we are not already buffering or seeking
    if (_playerState.noneOf(
        MuxPlayerState.BUFFERING,
        MuxPlayerState.REBUFFERING,
        MuxPlayerState.SEEKED
      ) && !seekingInProgress
    ) {
      if (_playerState == MuxPlayerState.PLAYING) {
        // If we were playing then the player buffers, that's re-buffering instead
        rebufferingStarted()
      } else {
        _playerState = MuxPlayerState.BUFFERING
        dispatch(TimeUpdateEvent(null))
      }
    }
  }

  /**
   * Call when the player prepares to play. That is, during the initialization and buffering, while
   * the caller intends for the video to play
   */
  fun play() {
    if (playEventsSent <= 0
      || (!seekingInProgress
              && _playerState.noneOf(MuxPlayerState.REBUFFERING, MuxPlayerState.SEEKED))
    ) {
      _playerState = MuxPlayerState.PLAY
      dispatch(PlayEvent(null))
    }
  }

  /**
   * Call when the player begins playing. Note that this state is distinct from [.play],
   * which is the state of preparing with intent to play
   * If seeking is in progress, this is ignored, when on seeked,state transitions to either playing,
   * or seeked from there
   * If rebuffering was in progress,
   */
  fun playing() {
    // Negative Logic Version
    if (seekingInProgress) {
      // We will dispatch playing event after seeked event
      Log.e("MuxStats", "Ignoring playing event, seeking in progress !!!")
      return
    }
    if (_playerState.oneOf(MuxPlayerState.PAUSED, MuxPlayerState.FINISHED_PLAYING_ADS)) {
      play()
    } else if (_playerState == MuxPlayerState.REBUFFERING) {
      rebufferingEnded()
    } else if( _playerState == MuxPlayerState.PLAYING) {
      // No need to re-enter the playing state
      return
    }

    _playerState = MuxPlayerState.PLAYING
    dispatch(PlayingEvent(null))
  }

  /**
   * Call when the player becomes paused.
   * If the player was rebuffering, then then an event will be sent to report that
   * If we were seeking, and the player becomes paused, the callers requested a pause during sync,
   *  which is already reported. Instead, we will move to the SEEKED state
   * Otherwise, we move to the PAUSED state and send a PauseEvent
   */
  fun pause() {
    // Process unless we're already paused
    if (_playerState == MuxPlayerState.SEEKED && pauseEventsSent > 0) {
      // No pause event after seeked
      return
    }
    if (_playerState == MuxPlayerState.REBUFFERING) {
      rebufferingEnded()
    }
    if (seekingInProgress) {
      seeked(false)
      return
    }
    _playerState = MuxPlayerState.PAUSED
    dispatch(PauseEvent(null))
  }

  /**
   * Call when the player has stopped seeking. This is normally handled automatically, but may need
   * to be called if there was an surprise position discontinuity in some cases
   *
   * This method can also infer a PLAYING state transition, if required. Use @param inferPlayingState
   */
  fun seeked(inferPlayingState: Boolean) {
    // Only handle if we were previously seeking
    if (seekingInProgress) {
      if (inferPlayingState) {
        // If inferring playing state, we may also assume the player is playing based on
        // collected state data
        if (
          (((System.currentTimeMillis() - firstFrameRenderedAtMillis
                  > timeToWaitAfterFirstFrameReceived) && firstFrameReceived) || !mediaHasVideoTrack!!)
          && seekingEventsSent > 0
        ) {
          // This is a playback !!!
          dispatch(SeekedEvent(null))
          seekingInProgress = false
          MuxLogger.d("MuxStats", "Playing called from seeked event !!!")
          playing()
        } else {
          // No playback yet.
          MuxLogger.d("MuxStats", "Seeked before playback started");
        }
      } else {
        // If not inferring player state, just dispatch the event
        dispatch(SeekedEvent(null))
        seekingInProgress = false
        _playerState = MuxPlayerState.SEEKED
      }


      if (seekingEventsSent == 0) {
        seekingInProgress = false
      }
    } else {
      Log.v("MuxStats", "WHY !!!!");
    }
  }

  /**
   * Called when the player starts seeking, or encounters a discontinuity.
   * If the player was playing, a PauseEvent will be dispatched.
   * In all cases, the state will move to SEEKING, and frame rendering data will be reset
   */
  fun seeking() {
    // TODO: We are getting a seeking() before the start of the view for some reason.
    //  This (I think?) causes state handling for another event to call seeked()
    //  We should eliminate the improper seeking() call, or find good criteria to ignore it,
    //  or reset seeking state (possibly other state data too) when ViewStart is dispatched (use an IEventListener I guess)
    if (playEventsSent == 0) {
      // Ignore this, we have received a seek event before we have received playerready event,
      // so this event should be ignored.
      return;
    }
    if (muxPlayerState == MuxPlayerState.PLAYING) {
      dispatch(PauseEvent(null))
    }
    _playerState = MuxPlayerState.SEEKING
    seekingInProgress = true
    firstFrameRenderedAtMillis = FIRST_FRAME_NOT_RENDERED
    dispatch(SeekingEvent(null))
    firstFrameReceived = false
  }

  /**
   * Call when the end of playback was reached.
   * A PauseEvent and EndedEvent will both be sent, and the state will be set to ENDED
   */
  fun ended() {
    dispatch(PauseEvent(null))
    dispatch(EndedEvent(null))
    _playerState = MuxPlayerState.ENDED
  }

  fun isPaused(): Boolean {
    return _playerState == MuxPlayerState.PAUSED
            || _playerState == MuxPlayerState.ENDED
            || _playerState == MuxPlayerState.ERROR
            || _playerState == MuxPlayerState.INIT
  }

  fun onFirstFrameRendered() {
    firstFrameRenderedAtMillis = System.currentTimeMillis()
    firstFrameReceived = true
  }

  fun internalError(error: Exception) {
    if (error is MuxErrorException) {
      dispatch(InternalErrorEvent(error.code, error.message))
    } else {
      dispatch(
        InternalErrorEvent(
          ERROR_UNKNOWN,
          "${error.javaClass.canonicalName} - ${error.message}"
        )
      )
    }
  }

  /**
   * Call when the media content was changed within the same stream, ie, the stream URL remained the
   * same but the content within changed, ie during a livestream. Does anyone remember shoutcast?
   *
   * This method will start a new Video View on Mux Data's backend
   */
  fun programChange(customerVideoData: CustomerVideoData) {
    reset()
    muxStats().programChange(customerVideoData)
  }

  /**
   * Call when the media stream (by URL) was changed.
   *
   * This method will start a new Video View on Mux Data's backend
   */
  fun videoChange(customerVideoData: CustomerVideoData) {
    _playerState = MuxPlayerState.INIT
    reset()
    muxStats().videoChange(customerVideoData)
  }

  fun renditionChange(
    advertisedBitrate: Int,
    advertisedFrameRate: Float,
    sourceWidth: Int,
    sourceHeight: Int
  ) {
    sourceAdvertisedBitrate = advertisedBitrate
    sourceAdvertisedFrameRate = advertisedFrameRate
    this.sourceWidth = sourceWidth
    this.sourceHeight = sourceHeight

    dispatch(RenditionChangeEvent(null))
  }

  /**
   * Call when an Ad begins playing
   */
  fun playingAds() {
    _playerState = MuxPlayerState.PLAYING_ADS
  }

  /**
   * Call when all ads are finished being played
   */
  fun finishedPlayingAds() {
    _playerState = MuxPlayerState.FINISHED_PLAYING_ADS
  }

  fun onMainPlaylistTags(tags: List<SessionTag>) {
    // dispatch new session data on change only
    if (sessionTags != tags) {
      sessionTags = tags
      muxStats().setSessionData(tags)
    }
  }

  /**
   * Kills this object. After being killed, this object will no longer report metrics to Mux Data
   */
  fun release() {
    positionWatcher?.stop("tracker released")
    dead = true
  }

  /**
   * Returns true if a frame was rendered by the player, or if frame rendering is not being tracked
   * @see #trackFirstFrameRendered
   */
  private fun firstFrameRendered(): Boolean = !trackFirstFrameRendered
          || (firstFrameReceived && (System.currentTimeMillis() - firstFrameRenderedAtMillis > FIRST_FRAME_WAIT_MILLIS))

  /**
   * Called internally when the player starts rebuffering. Rebuffering is buffering after the
   * initial content had started playing
   */
  private fun rebufferingStarted() {
    _playerState = MuxPlayerState.REBUFFERING
    dispatch(RebufferStartEvent(null))
  }

  /**
   * Called internally when the player finishes rebuffering. Callers are responsible for setting the
   * new appropriate player state
   */
  private fun rebufferingEnded() {
    dispatch(RebufferEndEvent(null))
  }

  private fun reset() {
    mimeType = null
    playEventsSent = 0
    pauseEventsSent = 0
    totalEventsSent = 0
    seekingEventsSent = 0
    seekedEventsSent = 0
    firstFrameReceived = false
    firstFrameRenderedAtMillis = FIRST_FRAME_NOT_RENDERED
    currentTimelineWindow = Timeline.Window()
    allowedHeaders.clear()
  }

  // TODO see how to avoid making this public
  public fun dispatch(event: IEvent) {
    // TODO: State collector holds onto nothing, no need to make it dead()
//    if (dead) {
//      MuxLogger.w(logTag(), "event sent after release: ${event.debugString}")
//      return
//    }
    totalEventsSent++
    when (event.type) {
      PlayEvent.TYPE -> {
        playEventsSent++
      }
      PauseEvent.TYPE -> {
        pauseEventsSent++
      }
      SeekingEvent.TYPE -> {
        seekingEventsSent++
      }
    }
    dispatcher.dispatch(event)
  }

  /**
   * Manages a timer loop in a coroutine scope that periodically polls the player for its current
   * playback position. The polling is done from the main thread.
   *
   * This object should be stopped when no longer needed. To handle cases where users forget to
   * release our SDK, implementations should not hold strong references to big objects like context
   * or a player. If [getTimeMillis] returns null, this object will automatically stop
   *
   * Stops if:
   *  the caller calls [stop]
   *  the superclass starts returning null from [getTimeMillis]
   */
  abstract class PositionWatcher(
    val updateIntervalMillis: Long,
    val stateCollector: MuxStateCollectorBase
  ) {
    private val timerScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    protected abstract fun getTimeMillis(): Long?

    fun stop(message: String) {
      timerScope.cancel(message)
    }

    fun start() {
      timerScope.launch {
        while (true) {
          updateOnMain(this)
          delay(updateIntervalMillis)
        }
      }
    }

    private fun updateOnMain(coroutineScope: CoroutineScope) {
      coroutineScope.launch(Dispatchers.Main) {
        val position = getTimeMillis()
        if (position != null) {
          stateCollector.playbackPositionMills = position
          // pick up seeked events that may not otherwise be delivered in sequence
          if (stateCollector.seekingInProgress) {
            stateCollector.seeked(true)
          }
        } else {
          // If the data source is returning null, assume caller cleaned up the player
          MuxLogger.d(logTag(), "PlaybackPositionWatcher: Player lost. Stopping")
          stop("player lost")
        }
      }
    }
  }
}
