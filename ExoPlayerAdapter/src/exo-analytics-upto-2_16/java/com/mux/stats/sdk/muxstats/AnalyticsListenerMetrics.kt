package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.logTag
import com.mux.stats.sdk.muxstats.internal.weak

// TODO: Write a player binding
private class AnalyticsListenerBindingUpTo14 : MuxPlayerAdapter.PlayerBinding<SimpleExoPlayer> {

  private var listener: AnalyticsListener? by weak(null)

  init {
    MuxLogger.d(logTag(), "created");
  }

  override fun bindPlayer(player: SimpleExoPlayer, collector: MuxPlayerStateTracker) {
  }

  override fun unbindPlayer(player: SimpleExoPlayer, collector: MuxPlayerStateTracker) {
    listener?.let { player.removeAnalyticsListener(it) }
  }

}