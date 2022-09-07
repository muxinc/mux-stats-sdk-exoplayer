package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.ExoPlayer
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.logTag
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Binding for Player State metrics.
 * This implementation works from 2.16.1 to now (as of 4/22/22)
 */
private class PlayerStateMetrics216ToNow : MuxPlayerAdapter.PlayerBinding<ExoPlayer> {

  private var playerListener: MuxPlayerAdapter.PlayerBinding<ExoPlayer>? by weak(null)

  init {
    MuxLogger.d(logTag(), "created");
  }

  override fun bindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    playerListener = analyticsListenerMetrics().also {
      it.bindPlayer(player, collector)
    }
  }

  override fun unbindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    playerListener?.unbindPlayer(player, collector)
  }
}

/**
 * Generates a player binding for core exoplayer metrics.
 */
@JvmSynthetic
internal fun playerStateMetrics(): MuxPlayerAdapter.PlayerBinding<ExoPlayer> =
  PlayerStateMetrics216ToNow();
