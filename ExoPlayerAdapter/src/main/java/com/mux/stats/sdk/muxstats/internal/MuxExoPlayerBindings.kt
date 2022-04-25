package com.mux.stats.sdk.muxstats.internal

import com.google.android.exoplayer2.Player
import com.mux.stats.sdk.muxstats.MuxPlayerAdapter

/**
 * Generates PlayerBindings for ExoPlayer
 */
object MuxExoPlayerBindings {

  private fun coreBinding() = basicExoMetrics()

  // TODO: Variant-sensitive. Delegate to a global internal fun
  private fun basicExoMetrics(): Any {
    return ":("
  }
}

private data class BasicMetricsBindings(
  val coreBinding: MuxPlayerAdapter.PlayerBinding<Player>
)
