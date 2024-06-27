package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.exoplayeradapter.MuxPlayerAdapter
import com.mux.stats.sdk.muxstats.exoplayeradapter.internal.logTag
import com.mux.stats.sdk.muxstats.exoplayeradapter.internal.watchContentPosition
import com.mux.stats.sdk.muxstats.exoplayeradapter.internal.weak
import com.mux.stats.sdk.muxstats.internal.exoAnalyticsListener

private class AnalyticsListenerBindingUpTo16 : MuxPlayerAdapter.PlayerBinding<SimpleExoPlayer> {

  private var listener: AnalyticsListener? by weak(null)

  init {
    MuxLogger.d(logTag(), "created");
  }

  override fun bindPlayer(player: SimpleExoPlayer, collector:  MuxStateCollectorBase) {
    listener = exoAnalyticsListener(player, collector).also {
      player.addAnalyticsListener(it)
      player.watchContentPosition(collector)
    }
  }

  override fun unbindPlayer(player: SimpleExoPlayer, collector:  MuxStateCollectorBase) {
    listener?.let { player.removeAnalyticsListener(it) }
    collector.positionWatcher?.stop("unbound")
    listener = null
  }
}

@JvmSynthetic // Hides from java callers outside the module
internal fun analyticsListenerMetrics()
        : MuxPlayerAdapter.PlayerBinding<SimpleExoPlayer> = AnalyticsListenerBindingUpTo16()
