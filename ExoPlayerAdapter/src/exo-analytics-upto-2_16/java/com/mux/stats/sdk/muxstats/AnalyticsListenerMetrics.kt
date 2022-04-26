package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.exoAnalyticsListener
import com.mux.stats.sdk.muxstats.internal.logTag
import com.mux.stats.sdk.muxstats.internal.weak

private class AnalyticsListenerBindingUpTo16 : MuxPlayerAdapter.PlayerBinding<SimpleExoPlayer> {

  private var listener: AnalyticsListener? by weak(null)

  init {
    MuxLogger.d(logTag(), "created");
  }

  override fun bindPlayer(player: SimpleExoPlayer, collector: MuxPlayerStateTracker) {
    listener = exoAnalyticsListener(player, collector).also {
      player.addAnalyticsListener(it)
    }
  }

  override fun unbindPlayer(player: SimpleExoPlayer, collector: MuxPlayerStateTracker) {
    listener?.let { player.removeAnalyticsListener(it) }
    listener = null
  }
}

@JvmSynthetic // Hides from java callers outside the module
internal fun analyticsListenerMetrics()
        : MuxPlayerAdapter.PlayerBinding<SimpleExoPlayer> = AnalyticsListenerBindingUpTo16()
