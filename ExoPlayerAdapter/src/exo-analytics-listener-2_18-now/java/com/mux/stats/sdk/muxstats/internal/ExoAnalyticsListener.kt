package com.mux.stats.sdk.muxstats.internal

import android.util.Log
import android.view.Surface
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
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
    eventTime: AnalyticsListener.EventTime,
    mediaLoadData: MediaLoadData
  ) {
    if (collector.detectMimeType) {
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

  override fun onRenderedFirstFrame(eventTime: EventTime, output: Any, renderTimeMs: Long) {
    collector.onFirstFrameRendered()
  }

  override fun onPlaybackStateChanged(
    eventTime: AnalyticsListener.EventTime,
    state: Int,
  ) {
    player?.let { collector.handleExoPlaybackState(it.playbackState, it.playWhenReady) }
  }

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
    // TODO: This the new way (over position discontinuity or guessing) so figure out how to use it
    //collector.seeked(false)
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
    if (loadEventInfo.uri != null) {
      bandwidthMetricCollector
        .onLoadCanceled(
          loadEventInfo.loadTaskId,
          loadEventInfo.uri.path,
          loadEventInfo.responseHeaders
        )
    }
  }

  override fun onLoadCompleted(
    eventTime: EventTime,
    loadEventInfo: LoadEventInfo,
    mediaLoadData: MediaLoadData
  ) {
    if (loadEventInfo.uri != null) {
      bandwidthMetricCollector.onLoadCompleted(
        loadEventInfo.loadTaskId,
        loadEventInfo.uri.path,
        loadEventInfo.bytesLoaded,
        mediaLoadData.trackFormat,
        loadEventInfo.responseHeaders
      )
    }
  }

  override fun onLoadError(
    eventTime: EventTime,
    loadEventInfo: LoadEventInfo,
    mediaLoadData: MediaLoadData,
    e: IOException,
    wasCanceled: Boolean
  ) {
    bandwidthMetricCollector.onLoadError(loadEventInfo.loadTaskId, loadEventInfo.uri.path, e)
  }

  override fun onLoadStarted(
    eventTime: EventTime,
    loadEventInfo: LoadEventInfo,
    mediaLoadData: MediaLoadData
  ) {
    if (loadEventInfo.uri != null) {
      var segmentMimeType: String? = "unknown"
      var segmentWidth = 0;
      var segmentHeight = 0;
      if (mediaLoadData.trackFormat != null && mediaLoadData.trackFormat!!.sampleMimeType != null) {
        segmentMimeType = mediaLoadData.trackFormat!!.sampleMimeType
      }
      if (mediaLoadData.trackFormat != null) {
        segmentWidth = mediaLoadData.trackFormat!!.width;
        segmentHeight = mediaLoadData.trackFormat!!.height;
      }
      bandwidthMetricCollector
        .onLoadStarted(
          loadEventInfo.loadTaskId, mediaLoadData.mediaStartTimeMs,
          mediaLoadData.mediaEndTimeMs, loadEventInfo.uri.path, mediaLoadData.dataType,
          loadEventInfo.uri.host, segmentMimeType, segmentWidth, segmentHeight
        )
    }
  }
}

@JvmSynthetic
internal fun <P : ExoPlayer> exoAnalyticsListener(player: P, collector: MuxStateCollector)
        : AnalyticsListener = ExoAnalyticsListener(player, collector)

