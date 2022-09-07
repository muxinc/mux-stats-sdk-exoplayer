package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.logTag
import com.mux.stats.sdk.muxstats.internal.watchContentPosition
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Binding for Player State metrics.
 * This version will attach an AnalyticsListener instead of a Player Listener if the player is a
 * SimpleExoPlayer.
 *
 * This implementation works up to exoplayer 2.15.1
 */
private class PlayerStateMetricsTo215 : MuxPlayerAdapter.PlayerBinding<ExoPlayer> {

  private var playerListener: MuxPlayerAdapter.PlayerBinding<ExoPlayer>? by weak(null)
  private var analyticsListener: MuxPlayerAdapter.PlayerBinding<SimpleExoPlayer>? by weak(null)

  init {
    MuxLogger.d(logTag(), "created");
  }

  override fun bindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    if (player is SimpleExoPlayer) {
      analyticsListener = analyticsListenerMetrics().also { it.bindPlayer(player, collector) }
    } else {
      playerListener = basicExoEvents().also { it.bindPlayer(player, collector) }
    }
    player.watchContentPosition(collector)
  }

  override fun unbindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    playerListener?.unbindPlayer(player, collector)
    collector.positionWatcher?.stop("unbound")
    if (player is SimpleExoPlayer) {
      analyticsListener?.unbindPlayer(player, collector)
    }
  }
}

/**
 * Generates a player binding for core exoplayer metrics.
 */
@JvmSynthetic
internal fun playerStateMetrics(): MuxPlayerAdapter.PlayerBinding<ExoPlayer> =
  PlayerStateMetricsTo215();
