package com.mux.stats.sdk.muxstats.internal

import com.google.android.exoplayer2.Player
import com.mux.stats.sdk.muxstats.MuxDataCollector
import com.mux.stats.sdk.muxstats.MuxPlayerState

/**
 * Handles a change of basic ExoPlayer state
 */
@JvmSynthetic // Hidden from Java callers, since the only ones are external
internal fun MuxDataCollector.handleExoPlaybackState(
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
