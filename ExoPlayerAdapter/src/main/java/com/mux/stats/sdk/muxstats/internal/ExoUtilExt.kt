package com.mux.stats.sdk.muxstats.internal

import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil
import com.mux.stats.sdk.muxstats.MuxErrorException
import com.mux.stats.sdk.muxstats.MuxPlayerState
import com.mux.stats.sdk.muxstats.MuxStateCollector

// -- General Utils --

/**
 * Returns true if the object is one of any of the parameters supplied
 */
@JvmSynthetic // Hide from Java callers because all are external
internal fun Any.oneOf(vararg accept: Any) = accept.contains(this)

/**
 * Returns true if the object is not any of the parameters supplied
 */
@JvmSynthetic // Hide from Java callers because all are external
internal fun Any.noneOf(vararg accept: Any) = !accept.contains(this)

/**
 * Gets a Log Tag from the name of the calling class. Can be used in any package that isn't
 * obfuscated (such as muxstats)
 */
internal inline fun <reified T> T.logTag() = T::class.java.simpleName

// -- ExoPlayer Helpers --

/**
 * Handles an ExoPlayer position discontinuity
 */
@JvmSynthetic // Hides from java
internal fun MuxStateCollector.handlePositionDiscontinuity(reason: Int) {
  when (reason) {
    Player.DISCONTINUITY_REASON_SEEK -> {
      // If they seek while paused, this is how we know the seek is complete
      if (muxPlayerState == MuxPlayerState.PAUSED
        // Seeks on audio-only media are reported this way instead
        || !mediaHasVideoTrack
      ) {
        seeked(false)
      }
    }
    else -> {} // ignored
  }
}
/**
 * Handles a change of basic ExoPlayer state
 */
@JvmSynthetic // Hidden from Java callers, since the only ones are external
internal fun MuxStateCollector.handleExoPlaybackState(
  playbackState: Int, // the @IntDef for player state omitted. Unavailable on all exo versions
  playWhenReady: Boolean
) {
  if (this.muxPlayerState == MuxPlayerState.PLAYING_ADS) {
    // Normal playback events are ignored during ad playback
    return
  }

  fun playOrPause(playWhenReady: Boolean) {
    if (playWhenReady) {
      play()
    } else {
      pause()
    }
  }

  when (playbackState) {
    Player.STATE_BUFFERING -> {
      buffering()
      playOrPause(playWhenReady)
    }
    Player.STATE_READY -> {
      playOrPause(playWhenReady)
    }
    Player.STATE_ENDED -> {
      ended()
    }
    Player.STATE_IDLE -> {
      if (muxPlayerState == MuxPlayerState.PLAY || muxPlayerState == MuxPlayerState.PLAYING) {
        // If we are playing/preparing to play and go idle, the player was stopped
        pause()
      }
    }
  } // when (playbackState)
} // fun handleExoPlaybackState

/**
 * Returns and starts an object that will poll ExoPlayer for its content position every so often
 * and updated the given MuxStateCollector
 */
@JvmSynthetic // Hidden from Java callers, since the only ones are external
internal fun ExoPlayer.watchContentPosition(stateCollector: MuxStateCollector):
        MuxStateCollector.PositionWatcher = ExoPositionWatcher(this, stateCollector)

@JvmSynthetic
internal fun MuxStateCollector.handleExoPlaybackException(e: ExoPlaybackException) {
  if (e.type == ExoPlaybackException.TYPE_RENDERER) {
    val cause = e.rendererException
    if (cause is MediaCodecRenderer.DecoderInitializationException) {
      val die = cause
      if (die.codecInfo == null) {
        if (die.cause is MediaCodecUtil.DecoderQueryException) {
          internalError(MuxErrorException(e.type, "Unable to query device decoders"))
        } else if (die.secureDecoderRequired) {
          internalError(MuxErrorException(e.type, "No secure decoder for " + die.mimeType))
        } else {
          internalError(MuxErrorException(e.type, "No decoder for " + die.mimeType))
        }
      } else {
        internalError(
          MuxErrorException(e.type, "Unable to instantiate decoder for " + die.mimeType)
        )
      } // if(die.codecInfo)..else
    } else {
      internalError(
        MuxErrorException(
          e.type,
          "${cause.javaClass.canonicalName} - ${cause.message}"
        )
      )
    } // if(cause is..)...else
  } else if (e.type == ExoPlaybackException.TYPE_SOURCE) {
    val error: Exception = e.sourceException
    internalError(
      MuxErrorException(
        e.type,
        "${error.javaClass.canonicalName} - ${error.message}"
      )
    )
  } else if (e.type == ExoPlaybackException.TYPE_UNEXPECTED) {
    val error: Exception = e.unexpectedException
    internalError(
      MuxErrorException(
        e.type,
        "${error.javaClass.canonicalName} - ${error.message}"
      )
    )
  } else {
    internalError(e)
  }
}

// -- private helper classes

/**
 * Watches an ExoPlayer's position, polling it every {@link #UPDATE_INTERVAL_MILIS} milliseconds
 */
private class ExoPositionWatcher(player: ExoPlayer, stateCollector: MuxStateCollector) :
  MuxStateCollector.PositionWatcher(
    UPDATE_INTERVAL_MILLIS, stateCollector
  ) {
  companion object {
    const val UPDATE_INTERVAL_MILLIS = 150L
  }
  private val player by weak(player) // don't hold the player because this object does async looping
  override fun getTimeMillis(): Long? = player?.contentPosition
}
