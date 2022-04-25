package com.mux.stats.sdk.muxstats

import com.mux.stats.sdk.core.events.EventBus
import com.mux.stats.sdk.core.events.IEvent
import com.mux.stats.sdk.core.events.InternalErrorEvent
import com.mux.stats.sdk.core.events.playback.*
import com.mux.stats.sdk.core.model.CustomerVideoData
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.logTag
import com.mux.stats.sdk.muxstats.internal.noneOf
import com.mux.stats.sdk.muxstats.internal.oneOf
import com.mux.stats.sdk.muxstats.internal.weak
import kotlinx.coroutines.*
import java.lang.Exception
import kotlin.properties.Delegates

/**
 * Collects events from a player and delivers them into a MuxStats instance.
 * As a player's state model may differ from that used by Mux Data products, the state as understood
 * by Mux Data is tracked here.
 *
 * You should supply one of these to {@link MuxPlayerAdapter}, and call is methods from your
 * {@link PlayerBinding<Player>}
 */
class MuxPlayerStateTracker(
  val muxStats: MuxStats,
  // TODO : I don't think we need this
  val uiDelegate: MuxUiDelegate<*>,
  // TODO: I don't think we need this
  val playerDelegate: IPlayerListener,
  val eventBus: EventBus,
  val trackFirstFrameRendered: Boolean = true,
) {

  companion object {
    const val DURATION_UNKNOWN = -1L
    const val ERROR_UNKNOWN = -1;
    const val ERROR_DRM = -2
    const val ERROR_IO = -3

    private const val FIRST_FRAME_NOT_RENDERED: Long = -1
    // Wait this long after the first frame was rendered before logic considers it rendered
    private const val FIRST_FRAME_WAIT_MILLIS = 50L
  }

  /**
   * The current state of the player, as represented by Mux Data
   */
  val muxPlayerState by ::_playerState
  private var _playerState = MuxPlayerState.INIT

  /**
   * Detected MIME type of the playing media, if applicable
   */
  var mimeType: String? = null

  /**
   * True if the media being played has a video stream, false if not
   * This is used to decide how to handle position discontinuities for audio-only streams
   * The default value is true, which might be fine to keep, depending on your player
   */
  var mediaHasVideoTrack: Boolean = true

  /**
   * Whether or not the MIME type of the playing media can be reported.
   * If playing DASH or HLS, this should be set to false, as the MIME type may vary according to
   * the segments.
   */
  var reportMimeType = true

  /**
   * Total duration of the media being played, in milliseconds
   */
  var sourceDurationMs: Long = DURATION_UNKNOWN

  /**
   * The current playback position of the player
   */
  var playbackPositionMills: Long = DURATION_UNKNOWN

  /**
   * An asynchronous watcher for playback position. It waits for the given update interval, and
   * then sets the {@link #playbackPositionMillis} property on this object. It can be stopped by
   * calling {@link #PositionWatcher.stop(String)}, and will automatically stop if it can no longer
   * access play time info
   */
  var positionWatcher: PositionWatcher?
          by Delegates.observable(null) { _, old, new ->
            old?.apply { stop("watcher replaced") }
            new?.start()
          }

  private var seekingInProgress = false // TODO: em - We have a SEEKING state so why do we have this
  private var firstFrameReceived = false
  private var firstFrameRenderedAtMillis = 0L // Based on system time

  private var pauseEventsSent = 0
  private var playEventsSent = 0
  private var totalEventsSent = 0

  private var dead = false

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
    // Skip during during seeking or buffering,
    //   unless it's the first play event, which should always be captured
    if (playEventsSent <= 0
      || (!seekingInProgress
              && _playerState.noneOf(MuxPlayerState.REBUFFERING, MuxPlayerState.SEEKED))
    ) {
      _playerState = MuxPlayerState.PLAY
      dispatch(PlayEvent(null))
    }
  }

  /**
   * Call when the player begins playing. Note that this state is distinct from {@link #play()},
   * which is the state of preparing with intent to play
   * If seeking is in progress, this is ignored, when on seeked,state transitions to either playing,
   * or seeked from there
   * If rebuffering was in progress,
   */
  fun playing() {
    // Don't processing playing() while we are seeking. We won't be playing until after seeked()
    //  and the player will update us after seek completes, which calls seeked() again
    if (!seekingInProgress) {
      if (_playerState.oneOf(MuxPlayerState.PAUSED, MuxPlayerState.FINISHED_PLAYING_ADS)) {
        play()
      } else if (_playerState == MuxPlayerState.REBUFFERING) {
        rebufferingEnded()
      }

      _playerState = MuxPlayerState.PLAYING
      dispatch(PlayingEvent(null))
    }
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
    if (_playerState != MuxPlayerState.PAUSED) {
      // Process unless we just seeked OR if this is our first pause event
      if (_playerState != MuxPlayerState.SEEKED || pauseEventsSent <= 0) {

        // If we were rebuffering and move to PAUSED, the rebuffering is over
        if (_playerState == MuxPlayerState.REBUFFERING) {
          rebufferingEnded()
        }
        // If we were seeking and moved to paused, the seek is over
        if (seekingInProgress) {
          seeked(false)
          return
        }

        _playerState = MuxPlayerState.PAUSED
        dispatch(PauseEvent(null))
      }
    }
  }

  /**
   * Call when the player has stopped seeking. This is normally handled automatically, but may need
   * to be called if there was an surprise position discontinuity in some cases
   *
   * If the seek completed after video frames were rendered, and first-frame detection is enabled,
   *  and inferSeekingOrPlaying is true, the new state will be PLAYING
   * If the seek completed before frames were rendered, or that metrics is not detected, the new
   *  state will be SEEKED
   */
  fun seeked(inferSeekingOrPlaying: Boolean) {
    // Only handle if we were previously seeking
    if (seekingInProgress) {
      // go to playing if we have rendered frames, otherwise go to seeked
      if (inferSeekingOrPlaying && firstFrameRendered()) {
        playing()
      } else {
        // If we haven't rendered any frames yet, go to seeked state
        _playerState = MuxPlayerState.SEEKED
      }

      seekingInProgress = false
      dispatch(SeekedEvent(null))
    }
  }

  /**
   * Called when the player starts seeking, or encounters a discontinuity.
   * If the player was playing, a PauseEvent will be dispatched.
   * In all cases, the state will move to SEEKING, and frame rendering data will be reset
   */
  fun seeking() {
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

  fun error(error: Exception) {
    if (error is MuxErrorException) {
      dispatch(InternalErrorEvent(error.code, error.message))
    } else {
      dispatch(InternalErrorEvent(ERROR_UNKNOWN,
        "${error.javaClass.canonicalName} - ${error.message}"))
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
    muxStats.programChange(customerVideoData)
  }

  /**
   * Call when the media stream (by URL) was changed.
   *
   * This mehtod will start a new Video View on Mux Data's backend
   */
  fun videoChange(customerVideoData: CustomerVideoData) {
    _playerState = MuxPlayerState.INIT
    reset()
    muxStats.videoChange(customerVideoData)
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
          || (System.currentTimeMillis() - firstFrameRenderedAtMillis > FIRST_FRAME_WAIT_MILLIS)

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
    reportMimeType = true
    playEventsSent = 0
    pauseEventsSent = 0
    totalEventsSent = 0
    firstFrameReceived = false
    firstFrameRenderedAtMillis = FIRST_FRAME_NOT_RENDERED
  }

  private fun dispatch(event: IEvent) {
    if (dead) {
      MuxLogger.w(logTag(), "event sent after release: $event")
      return
    }
    totalEventsSent++
    when (event.type) {
      PlayEvent.TYPE -> {
        playEventsSent++
      }
      PauseEvent.TYPE -> {
        pauseEventsSent++
      }
    }
    eventBus.dispatch(event)
  }

  /**
   * Manages a timer loop in a coroutine scope that periodically polls the player for its current
   * playback position. The polling is done from the main thread.
   *
   * This object should be stopped when no longer needed. To handle cases where users forget to
   * release our SDK, implementations should not hold strong references to big objects like context
   * or a player. If {@link #getTimeMillis()} returns null, this object will automatically stop
   *
   * Stops if:
   *  the caller calls {@link #stop(String)
   *  the superclass starts returning null from {@link getTimeMillis()}
   */
  abstract class PositionWatcher(
    val updateIntervalMillis: Long,
    val stateTracker: MuxPlayerStateTracker
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
          stateTracker.playbackPositionMills = position
        } else {
          // If the data source is returning null, assume caller cleaned up the player
          MuxLogger.d(logTag(), "PlaybackPositionWatcher: Player lost. Stopping")
          stop("player lost")
        }
      }
    }
  }
}
