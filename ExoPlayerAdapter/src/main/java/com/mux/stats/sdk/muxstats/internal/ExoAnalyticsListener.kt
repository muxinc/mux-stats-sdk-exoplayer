package com.mux.stats.sdk.muxstats.internal

import android.util.Log
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

  // TODO: Requires exo 2.12
  override fun onPlayWhenReadyChanged(
    eventTime: AnalyticsListener.EventTime,
    playWhenReady: Boolean,
    reason: Int
  ) {
    player?.let {
      Log.d(logTag(), "Playback State(from player): ${it.playbackState}, ")
      collector.handleExoPlaybackState(it.playbackState, it.playWhenReady) }
  }

  override fun onRenderedFirstFrame(
    eventTime: AnalyticsListener.EventTime,
    output: Any,
    renderTimeMs: Long
  ) {
    collector.onFirstFrameRendered()
  }

//  override fun onPlaybackStateChanged(
//    eventTime: AnalyticsListener.EventTime,
//    state: Int,
//  ) {
//    player?.let { collector.handleExoPlaybackState(it.playbackState, it.playWhenReady) }
//  }

  @Suppress("OVERRIDE_DEPRECATION") // The extra info is not required for our metrics
  override fun onPositionDiscontinuity(
    eventTime: AnalyticsListener.EventTime,
    reason: Int
  ) {
    collector.handlePositionDiscontinuity(reason)
  }

  @Suppress("OVERRIDE_DEPRECATION") // Not worth making a new variant over (deprecated 2.12)
  override fun onSeekStarted(eventTime: AnalyticsListener.EventTime) {
    collector.seeking()
  }

  @Suppress("OVERRIDE_DEPRECATION") // Not worth making a new variant over (deprecated 2.12)
  override fun onSeekProcessed(eventTime: AnalyticsListener.EventTime) {
    collector.seeked(false)
  }

  override fun onTimelineChanged(eventTime: AnalyticsListener.EventTime, reason: Int) {
    val player = player // strong reference during the listener call
    if (player != null) {
      Timeline.Window().apply {
        eventTime.timeline.getWindow(0, this)
        collector.sourceDurationMs = durationMs
      }
    }
  }

  @Suppress("OVERRIDE_DEPRECATION") // Not worth making a new variant over (deprecated 2.13)
  override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format) {
    // Format is nullable on some versions of exoplayer (though the framework probably won't supply that value)
    @Suppress("RedundantNullableReturnType") val optionalFormat: Format? = format
    optionalFormat?.let { fmt ->
      collector.renditionChange(
        advertisedBitrate = fmt.bitrate,
        advertisedFrameRate = fmt.frameRate,
        sourceHeight = fmt.height,
        sourceWidth = fmt.width
      )
    }
  }
}

@JvmSynthetic
internal fun <P : ExoPlayer> exoAnalyticsListener(player: P, collector: MuxStateCollector)
        : AnalyticsListener = ExoAnalyticsListener(player, collector)

