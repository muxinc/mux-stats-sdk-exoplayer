package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.Player
import com.mux.stats.sdk.muxstats.internal.handleExoPlaybackState
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Player binding for basic ExoPlayer metrics.
 * This implementation works from 2.14.1 to now (as of 4/22/22)
 *
 * NOTE: This is only used on ExoPlayer versions 2.15 and below. AnalyticsListener is preferred,
 * and its presence is guaranteed on our player object in exo 2.16 and higher
 */
private class BasicExoMetrics : MuxPlayerAdapter.PlayerBinding<Player> {

  private var playerListener: Player.Listener? = null

  override fun bindPlayer(player: Player, collector: MuxPlayerStateTracker) {
    playerListener = newListener({ player }, collector)
    // non-null guaranteed unless unbind() and bind() on different threads
    player.addListener(playerListener!!)
  }

  override fun unbindPlayer(player: Player) {
    playerListener?.let { player.removeListener(it) }
  }

  private fun newListener(playerSrc: () -> Player, collector: MuxPlayerStateTracker) =
    object : Player.Listener {
      val player by weak(playerSrc()) // player should be weakly reachable in case user doesn't clean up

      override fun onPlaybackStateChanged(playbackState: Int) {
        // We rely on the player's playWhenReady because the order of this callback and its callback
        //  is not well-defined
        player?.let { collector.handleExoPlaybackState(playbackState, it.playWhenReady) }
      }
    }
}

/**
 * Generates a player binding for core exoplayer metrics. Data comes from
 *
 * NOTE: This is only used on ExoPlayer versions 2.15 and below. AnalyticsListener is preferred,
 * and its presence is guaranteed on our player object in exo 2.16 and higher
 */
@JvmSynthetic
internal fun exoMetricsFromListener(): MuxPlayerAdapter.PlayerBinding<Player> = BasicExoMetrics();
