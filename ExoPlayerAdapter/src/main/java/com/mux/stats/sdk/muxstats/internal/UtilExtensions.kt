package com.mux.stats.sdk.muxstats.internal

import com.google.android.exoplayer2.Player
import com.mux.stats.sdk.muxstats.MuxPlayerState
import com.mux.stats.sdk.muxstats.MuxPlayerStateTracker

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
  }
}
