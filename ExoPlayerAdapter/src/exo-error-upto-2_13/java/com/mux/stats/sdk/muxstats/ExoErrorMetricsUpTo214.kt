package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.handleExoPlaybackException
import com.mux.stats.sdk.muxstats.internal.logTag
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Player binding for  exoplayer android metrics
 * This implementation works up until 2.14.1
 *
 * NOTE: This is only used on ExoPlayer versions 2.15 and below. AnalyticsListener is preferred,
 * and its presence is guaranteed on our player object in exo 2.16 and higher
 */
private class ExoErrorMetricsByListenerUpTo213 : MuxPlayerAdapter.PlayerBinding<ExoPlayer> {
  private var playerListener: Player.EventListener? by weak(null)

  init {
    MuxLogger.d(logTag(), "created");
  }

  override fun bindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    playerListener = newListener(collector).also { player.addListener(it) }
  }

  override fun unbindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    collector.positionWatcher?.stop("player unbound")
    collector.positionWatcher = null
    playerListener?.let { player.removeListener(it) }
  }

  private fun newListener(collector: MuxStateCollector) = ErrorPlayerListenerUpTo214(collector)
} // class ErrorPlayerBuListenerUpTo214

private class ErrorPlayerListenerUpTo214(val collector: MuxStateCollector) : Player.EventListener {
  override fun onPlayerError(error: ExoPlaybackException) {
    collector.handleExoPlaybackException(error.type, error)
  }
}

/**
 * Generates a player binding for exoplayer error metrics.
 */
@JvmSynthetic
internal fun playerErrorMetrics(): MuxPlayerAdapter.PlayerBinding<ExoPlayer> =
  ExoErrorMetricsByListenerUpTo213();
