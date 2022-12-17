package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.handleExoPlaybackException
import com.mux.stats.sdk.muxstats.internal.logTag
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Player binding for  exoplayer android metrics
 * This implementation works from 2.15.1 up until now (as of 4/25/2022)
 */
private class ExoErrorMetricsByListener215ToNow : MuxPlayerAdapter.PlayerBinding<ExoPlayer> {
  private var playerListener: Player.Listener? by weak(null)

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

private class ErrorPlayerListenerUpTo214(val collector: MuxStateCollector) : Player.Listener {
  override fun onPlayerError(error: PlaybackException) {
    if (error is ExoPlaybackException) {
      collector.handleExoPlaybackException(error.errorCode, error)
    } else {
      val errorMessage = "${error.errorCode}: ${error.message}"
      collector.internalError(MuxErrorException(error.errorCode, errorMessage))
    }
  }
}

/**
 * Generates a player binding for exoplayer error metrics.
 */
@JvmSynthetic
internal fun playerErrorMetrics(): MuxPlayerAdapter.PlayerBinding<ExoPlayer> =
  ExoErrorMetricsByListener215ToNow();
