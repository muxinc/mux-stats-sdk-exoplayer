package com.mux.stats.sdk.muxstats.internal

import android.util.Log
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.video.VideoSize
import com.mux.stats.sdk.muxstats.MuxStateCollector
import java.io.IOException

/**
 * There's only one required AnalyticsListener implementation (as of Exo 2.17) so it's here in the
 * common code.
 */
private class ExoAnalyticsListener(player: ExoPlayer, val collector: MuxStateCollector) :
  AnalyticsListener {

  private val player: ExoPlayer? by weak(player)

  private val bandwidthMetricCollector: BandwidthMetricDispatcher =
    BandwidthMetricDispatcher(player, collector)

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

  // TODO: Requires exo 2.12
  override fun onPlayWhenReadyChanged(
    eventTime: EventTime,
    playWhenReady: Boolean,
    reason: Int
  ) {
    player?.let {
      Log.d(logTag(), "Playback State(from player): ${it.playbackState}, ")
      collector.handleExoPlaybackState(it.playbackState, it.playWhenReady)
    }
  }

  override fun onRenderedFirstFrame(eventTime: EventTime, output: Any, renderTimeMs: Long) {
    collector.onFirstFrameRendered()
  }

  override fun onPlaybackStateChanged(
    eventTime: EventTime,
    state: Int,
  ) {
    player?.let { collector.handleExoPlaybackState(it.playbackState, it.playWhenReady) }
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
    if (player != null) {
      Timeline.Window().apply {
        eventTime.timeline.getWindow(0, this)
        collector.sourceDurationMs = durationMs
      }
    }
  }

  @Suppress("OVERRIDE_DEPRECATION") // Not worth making a new variant over (deprecated 2.13)
  override fun onVideoInputFormatChanged(eventTime: EventTime, format: Format) {
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

  override fun onTracksChanged(eventTime: EventTime, tracks: Tracks) {
    collector.mediaHasVideoTrack = player?.MuxMediaHasVideoTrack()
    collector.positionWatcher?.start()

    val mediaTrackGroups = tracks.groups.map { it.mediaTrackGroup }
    val asArray = Array(mediaTrackGroups.size) { mediaTrackGroups[it] }
    bandwidthMetricCollector.onTracksChanged(TrackGroupArray(*asArray))
  }

  override fun onVideoSizeChanged(
    eventTime: EventTime,
    videoSize: VideoSize
  ) {
    collector.sourceWidth = videoSize.width
    collector.sourceHeight = videoSize.height
  }

  override fun onLoadCanceled(
    eventTime: EventTime,
    loadEventInfo: LoadEventInfo,
    mediaLoadData: MediaLoadData
  ) {
    @Suppress("SENSELESS_COMPARISON")
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
    @Suppress("SENSELESS_COMPARISON")
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
    @Suppress("SENSELESS_COMPARISON")
    if (loadEventInfo.uri != null) {
      var segmentMimeType = "unknown"
      var segmentWidth = 0
      var segmentHeight = 0
      mediaLoadData.trackFormat?.let { format ->
        format.sampleMimeType?.let { segmentMimeType = it }
        segmentWidth = format.width
        segmentHeight = format.height
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

