package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.Player

/**
 * Player binding for basic ExoPlayer metrics.
 *
 * NOTE: This is only used on ExoPlayer versions 2.15 and below. AnalyticsListener is preferred,
 * and its presence is guaranteed on our player object in exo 2.16 and higher
 */
private class BasicExoMetrics : MuxPlayerAdapter.PlayerBinding<Player> {

  var playerListener: Player.Listener? = null

  override fun bindPlayer(player: Player, collector: MuxPlayerStateTracker) {

  }

  override fun unbindPlayer(player: Player) {
    TODO("Not yet implemented")
  }

  private fun newListener(player: Player, collector: MuxPlayerStateTracker) = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
    }
  }
}

/**
 * Generates a player binding for core exoplayer metrics
 *
 * NOTE: This is only used on ExoPlayer versions 2.15 and below. AnalyticsListener is preferred,
 * and its presence is guaranteed on our player object in exo 2.16 and higher
 */
@JvmSynthetic
internal fun basicExoMetrics(): MuxPlayerAdapter.PlayerBinding<Player> = BasicExoMetrics();
