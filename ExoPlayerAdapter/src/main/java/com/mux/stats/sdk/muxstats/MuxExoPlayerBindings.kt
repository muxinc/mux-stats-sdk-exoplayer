package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.Player

/**
 * Generates PlayerBindings for ExoPlayer
 */
object MuxExoPlayerBindings {

  private fun coreBinding() = basicExoMetrics()
}

private data class BasicMetricsBindings(
  val coreBinding: MuxPlayerAdapter.PlayerBinding<Player>
)
