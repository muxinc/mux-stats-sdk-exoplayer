package com.mux.stats.sdk.muxstats.internal

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.source.MediaLoadData
import com.mux.stats.sdk.muxstats.MuxPlayerStateTracker
import java.lang.Exception

/**
 * There's only one required AnalyticsListener implementation (as of Exo 2.17) so it's here in the
 * common code.
 */
private class ExoAnalyticsListener(player: ExoPlayer, val collector: MuxPlayerStateTracker) :
  AnalyticsListener {

  private val player: ExoPlayer? by weak(player)

  override fun onDownstreamFormatChanged(
    eventTime: AnalyticsListener.EventTime,
    mediaLoadData: MediaLoadData
  ) {
    if (collector.reportMimeType) {
      mediaLoadData.trackFormat?.containerMimeType?.let { collector.mimeType = it }
    }
  }

  override fun onDrmSessionManagerError(eventTime: AnalyticsListener.EventTime, error: Exception) {
  }
}

@JvmSynthetic
internal fun <P : ExoPlayer> exoAnalyticsListener(player: P, collector: MuxPlayerStateTracker)
        : AnalyticsListener = ExoAnalyticsListener(player, collector)

