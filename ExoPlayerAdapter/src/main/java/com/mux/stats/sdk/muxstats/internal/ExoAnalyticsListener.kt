package com.mux.stats.sdk.muxstats.internal

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.source.MediaLoadData
import com.mux.stats.sdk.muxstats.MuxStateCollector

/**
 * There's only one required AnalyticsListener implementation (as of Exo 2.17) so it's here in the
 * common code.
 */
private class ExoAnalyticsListener(player: ExoPlayer, val collector: MuxStateCollector) :
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

  override fun onPlaybackParametersChanged(
    eventTime: AnalyticsListener.EventTime,
    playbackParameters: PlaybackParameters
  ) {
    //todo
  }

  override fun onPlayWhenReadyChanged(
    eventTime: AnalyticsListener.EventTime,
    playWhenReady: Boolean,
    reason: Int
  ) {
    player?.let { collector.handleExoPlaybackState(it.playbackState, playWhenReady) }
  }

  override fun onPositionDiscontinuity(
    eventTime: AnalyticsListener.EventTime,
    reason: Int
  ) {
    collector.handlePositionDiscontinuity(reason)
  }

  override fun onTimelineChanged(eventTime: AnalyticsListener.EventTime, reason: Int) {
    val player = player // strong reference during the listener call
    if (player != null) {
      Timeline.Window().apply {
        collector.sourceDurationMs = durationMs
      }
    }
  }

  @Suppress("OVERRIDE_DEPRECATION") // Not worth making a new variant over
  override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format) {
    // TODO: Handle this
  }
}

@JvmSynthetic
internal fun <P : ExoPlayer> exoAnalyticsListener(player: P, collector: MuxStateCollector)
        : AnalyticsListener = ExoAnalyticsListener(player, collector)

