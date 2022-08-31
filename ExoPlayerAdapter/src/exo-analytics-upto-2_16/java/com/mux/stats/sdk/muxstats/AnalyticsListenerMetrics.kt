package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.*

private class AnalyticsListenerBindingUpTo16 : MuxPlayerAdapter.PlayerBinding<SimpleExoPlayer> {

  private var listener: AnalyticsListener? by weak(null)

  init {
    MuxLogger.d(logTag(), "created");
  }

  override fun bindPlayer(player: SimpleExoPlayer, collector: MuxStateCollector) {
    listener = exoAnalyticsListener(player, collector).also {
      player.addAnalyticsListener(it)
      player.watchContentPosition(collector)
    }
  }

  override fun unbindPlayer(player: SimpleExoPlayer, collector: MuxStateCollector) {
    listener?.let { player.removeAnalyticsListener(it) }
    collector.positionWatcher?.stop("unbound")
    listener = null
  }

//  override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
//    collector.sourceWidth = videoSize.width
//    collector.sourceHeight = videoSize.height
//  }

  override fun onVideoSizeChanged(
    eventTime: AnalyticsListener.EventTime,
    width: Int,
    height: Int,
    unappliedRotationDegrees: Int,
    pixelWidthHeightRatio: Float
  ) {
  }
}

@JvmSynthetic // Hides from java callers outside the module
internal fun analyticsListenerMetrics()
        : MuxPlayerAdapter.PlayerBinding<SimpleExoPlayer> = AnalyticsListenerBindingUpTo16()
