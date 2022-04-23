package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.Player

/**
 * Player binding for basic ExoPlayer metrics
 */
private class CoreExoMetrics : MuxPlayerAdapter.PlayerBinding<Player> {

  var playerListener: Player.Listener? = null

  override fun bindPlayer(player: Player, collector: MuxDataCollector) {

  }

  override fun unbindPlayer(player: Player) {
    TODO("Not yet implemented")
  }

  private fun newListener(player: Player, collector: MuxDataCollector) = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
    }
  }
}

@JvmSynthetic
internal fun coreExoMetrics(): MuxPlayerAdapter.PlayerBinding<Player> = CoreExoMetrics();
