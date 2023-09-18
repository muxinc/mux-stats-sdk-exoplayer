package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.exoAnalyticsListener
import com.mux.stats.sdk.muxstats.internal.logTag
import com.mux.stats.sdk.muxstats.internal.watchContentPosition

/**
 * Binding to an ExoPlayer using AnalyticsListener
 */
private class AnalyticsListenerBinding216ToNow : MuxPlayerAdapter.PlayerBinding<ExoPlayer> {

  private var listener: AnalyticsListener? = null//by weak(null)

  init {
    MuxLogger.d(logTag(), "created");
  }

  override fun bindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    listener = exoAnalyticsListener(player, collector).also {
      player.addAnalyticsListener(it)
      player.watchContentPosition(collector)
    }
  }

  override fun unbindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    listener?.let { player.removeAnalyticsListener(it) }
    collector.positionWatcher?.stop("unbound")
  }

}


@JvmSynthetic // Hides from java callers outside the module
internal fun analyticsListenerMetrics()
        : MuxPlayerAdapter.PlayerBinding<ExoPlayer> = AnalyticsListenerBinding216ToNow()
