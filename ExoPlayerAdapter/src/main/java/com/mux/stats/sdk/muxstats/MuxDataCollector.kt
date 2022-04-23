package com.mux.stats.sdk.muxstats

import com.mux.stats.sdk.core.events.EventBus
import com.mux.stats.sdk.core.events.IEvent
import com.mux.stats.sdk.core.events.playback.PauseEvent
import com.mux.stats.sdk.core.events.playback.PlayEvent
import com.mux.stats.sdk.core.util.MuxLogger

/**
 * Collects events from a player and delivers them into a MuxStats instance
 */
class MuxDataCollector(
        val muxStats: MuxStats,
        val uiDelegate: MuxUiDelegate<*>,
        val playerDelegate: IPlayerListener,
        val eventBus: EventBus,
        val trackFirstFrameRendered: Boolean = true,
) {

  companion object{
    private const val TAG = "MuxDataCollector"
    private const val FIRST_FRAME_NOT_RENDERED: Long = -1
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
   * Whether or not the MIME type of the playing media can be reported.
   * If playing DASH or HLS, this should be set to false, as the MIME type may vary according to
   * the segments.
   */
  // TODO: em - This is detectable using the same stuff that feeds Bandwidth Metrics
  var reportMimeType = true

  private var seekingInProgress = false
  private var firstFrameReceived = false
  private var firstFrameRenderedAtMillis = 0L // Based on system time

  private var pauseEventsSent = 0
  private var playEventsSent = 0
  private var totalEventsSent = 0

  private var dead = false

  fun buffering() {

  }

  fun play() {

  }

  /**
   * Call when the player becomes paused
   */
  fun pause() {
    if (muxPlayerState == MuxPlayerState.PAUSED
            || (muxPlayerState == MuxPlayerState.SEEKED && pauseEventsSent > 0)) {
      // No pause should be processed after seeked, unless it's the first one
      // Or if the player is already paused
      return
    }
    if (muxPlayerState == MuxPlayerState.REBUFFERING) {
      rebufferingEnded()
    }
    if (seekingInProgress) {
      seeked(false)
      return
    }

    _playerState = MuxPlayerState.PAUSED
    dispatch(PauseEvent(null))
  }

  fun ended() {
  }

  fun release() {
    dead = true
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
      MuxLogger.w(TAG, "event sent after release: $event")
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
  }
}
