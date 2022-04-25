package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.logTag
import com.mux.stats.sdk.muxstats.internal.weak

// TODO: Write a player binding
//  Use the common listener
// TODO: Test that this builds lmao
private class AnalyticsListenerBinding216ToNow : MuxPlayerAdapter.PlayerBinding<ExoPlayer> {

  private var listener: AnalyticsListener? by weak(null)

  init {
    MuxLogger.d(logTag(), "created");
  }

  override fun bindPlayer(player: ExoPlayer, collector: MuxPlayerStateTracker) {
    listener = exoAnalyticsListener(player, collector).also {
      player.addAnalyticsListener(it)
    }

    override fun unbindPlayer(player: ExoPlayer, collector: MuxPlayerStateTracker) {
      listener?.let { player.removeAnalyticsListener(it) }
      listener = null
    }

  }
}

@JvmSynthetic // Hides from java callers outside the module
internal fun analyticsListenerMetrics()
        : MuxPlayerAdapter.PlayerBinding<ExoPlayer> = AnalyticsListenerBindingUpTo14()
