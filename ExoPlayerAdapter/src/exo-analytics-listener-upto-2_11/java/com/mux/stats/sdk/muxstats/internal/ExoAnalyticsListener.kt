package com.mux.stats.sdk.muxstats.internal

import android.util.Log
import android.view.Surface
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime
import com.google.android.exoplayer2.source.MediaSourceEventListener.LoadEventInfo
import com.google.android.exoplayer2.source.MediaSourceEventListener.MediaLoadData
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.mux.stats.sdk.muxstats.MuxStateCollector
import java.io.IOException

/**
 * There's only one required AnalyticsListener implementation (as of Exo 2.17) so it's here in the
 * common code.
 */
private class ExoAnalyticsListener(player: ExoPlayer, val collector: MuxStateCollector) :
  AnalyticsListener {

  private val player: ExoPlayer? by weak(player)

  private val bandwidthMetricCollector: BandwidthMetricDispatcher = BandwidthMetricDispatcher(player, collector)

  override fun onDownstreamFormatChanged(
    eventTime: EventTime,
    mediaLoadData: MediaLoadData
  ) {
    if (collector.detectMimeType) {
      mediaLoadData.trackFormat?.containerMimeType?.let { collector.mimeType = it }
    }
  }

  override fun onPlaybackParametersChanged(
    eventTime: EventTime,
    playbackParameters: PlaybackParameters
  ) {
    //todo
  }

  override fun onPlayerStateChanged(
    eventTime: EventTime, playWhenReady: Boolean, playbackState: Int
  ) {
    player?.let {
      Log.d(logTag(), "Playback State(from player): ${it.playbackState}, ")
      collector.handleExoPlaybackState(it.playbackState, it.playWhenReady) }
  }

  override fun onRenderedFirstFrame(eventTime: EventTime, surface: Surface?) {
    collector.onFirstFrameRendered()
  }

  @Suppress("OVERRIDE_DEPRECATION") // The extra info is not required for our metrics
  override fun onPositionDiscontinuity(
    eventTime: EventTime,
    reason: Int
  ) {
    collector.handlePositionDiscontinuity(reason)
  }

  @Suppress("OVERRIDE_DEPRECATION") // Not worth making a new variant over (deprecated 2.12)
  override fun onSeekStarted(eventTime: EventTime) {
    collector.seeking()
  }

  @Suppress("OVERRIDE_DEPRECATION") // Not worth making a new variant over (deprecated 2.12)
  override fun onSeekProcessed(eventTime: EventTime) {
    // TODO: This the new way (over position discontinuity or guessing) so figure out how to use it
    //collector.seeked(false)
  }

  override fun onTimelineChanged(eventTime: EventTime, reason: Int) {
    val player = player // strong reference during the listener call
    if (player != null && eventTime.timeline.windowCount > 0) {
      Timeline.Window().apply {
        eventTime.timeline.getWindow(0, this)
        collector.sourceDurationMs = durationMs
      }
    }
  }

  override fun onDecoderInputFormatChanged(eventTime: EventTime, trackType: Int, format: Format) {
    collector.renditionChange(
      advertisedBitrate = format.bitrate,
      advertisedFrameRate = format.frameRate,
      sourceHeight = format.height,
      sourceWidth = format.width,
    )
  }

  @Deprecated("OVERRIDE_DEPRECATION") // Not worth making a new variant over (deprecated 2.13)
  override fun onTracksChanged(eventTime: EventTime, trackGroups: TrackGroupArray,
                               trackSelections: TrackSelectionArray) {
    collector.mediaHasVideoTrack = player?.MuxMediaHasVideoTrack();
    collector.positionWatcher?.start();
    bandwidthMetricCollector.onTracksChanged(trackGroups)
  }

  override fun onVideoSizeChanged(
    eventTime: EventTime,
    width: Int,
    height: Int,
    unappliedRotationDegrees: Int,
    pixelWidthHeightRatio: Float
  ) {
    collector.sourceWidth = width
    collector.sourceHeight = height
  }

  override fun onLoadCanceled(
    eventTime: EventTime,
    loadEventInfo: LoadEventInfo,
    mediaLoadData: MediaLoadData
  ) {
  }

  override fun onLoadCompleted(
    eventTime: EventTime,
    loadEventInfo: LoadEventInfo,
    mediaLoadData: MediaLoadData
  ) {
  }

  override fun onLoadError(
    eventTime: EventTime,
    loadEventInfo: LoadEventInfo,
    mediaLoadData: MediaLoadData,
    e: IOException,
    wasCanceled: Boolean
  ) {
  }

  override fun onLoadStarted(
    eventTime: EventTime,
    loadEventInfo: LoadEventInfo,
    mediaLoadData: MediaLoadData
  ) {
  }
}

@JvmSynthetic
internal fun <P : ExoPlayer> exoAnalyticsListener(player: P, collector: MuxStateCollector)
        : AnalyticsListener = ExoAnalyticsListener(player, collector)

