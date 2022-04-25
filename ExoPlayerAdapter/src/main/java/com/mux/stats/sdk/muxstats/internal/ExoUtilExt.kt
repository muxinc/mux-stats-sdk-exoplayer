package com.mux.stats.sdk.muxstats.internal

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.mux.stats.sdk.muxstats.MuxPlayerState
import com.mux.stats.sdk.muxstats.MuxPlayerStateTracker
import com.mux.stats.sdk.muxstats.internal.weak

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
 * Handles a change of basic ExoPlayer state
 */
@JvmSynthetic // Hidden from Java callers, since the only ones are external
internal fun MuxPlayerStateTracker.handleExoPlaybackState(
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

@JvmSynthetic // Hidden from Java callers, since the only ones are external
internal fun ExoPlayer.watchContentPosition(stateTracker: MuxPlayerStateTracker):
        MuxPlayerStateTracker.PositionWatcher = ExoPositionWatcher(this, stateTracker)

/**
 * Watches an ExoPlayer's position, polling it every {@link #UPDATE_INTERVAL_MILIS} milliseconds
 */
private class ExoPositionWatcher(player: ExoPlayer, stateTracker: MuxPlayerStateTracker) :
  MuxPlayerStateTracker.PositionWatcher(
    UPDATE_INTERVAL_MILLIS, stateTracker
  ) {
  companion object {
    const val UPDATE_INTERVAL_MILLIS = 150L
  }
  private val player by weak(player) // don't hold the player because this object does async looping
  override fun getTimeMillis(): Long? = player?.contentPosition
}
