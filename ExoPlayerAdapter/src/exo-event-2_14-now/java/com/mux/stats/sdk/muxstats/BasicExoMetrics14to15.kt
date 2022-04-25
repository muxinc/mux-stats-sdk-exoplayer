package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.mux.stats.sdk.muxstats.internal.handleExoPlaybackState
import com.mux.stats.sdk.muxstats.internal.watchContentPosition
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Player binding for basic ExoPlayer metrics.
 * This implementation works from 2.14.1 to now (as of 4/22/22)
 *
 * NOTE: This is only used on ExoPlayer versions 2.15 and below. AnalyticsListener is preferred,
 * and its presence is guaranteed on our player object in exo 2.16 and higher
 */
private class BasicExoMetrics14to15 : MuxPlayerAdapter.PlayerBinding<ExoPlayer> {

  private var playerListener: Player.Listener? = null

  override fun bindPlayer(player: ExoPlayer, collector: MuxPlayerStateTracker) {
    playerListener = newListener({ player }, collector)
    // non-null guaranteed unless unbind() and bind() on different threads
    player.addListener(playerListener!!)
  }

  override fun unbindPlayer(player: ExoPlayer, collector: MuxPlayerStateTracker) {
    collector.positionWatcher?.stop("player unbound")
    collector.positionWatcher = null
    playerListener?.let { player.removeListener(it) }
  }

  private fun newListener(playerSrc: () -> ExoPlayer, collector: MuxPlayerStateTracker) =
    object : Player.Listener {
      val player by weak(playerSrc()) // player should be weakly reachable in case user doesn't clean up

      override fun onPlaybackStateChanged(playbackState: Int) {
        // We rely on the player's playWhenReady because the order of this callback and its callback
        //  is not well-defined
        player?.let { collector.handleExoPlaybackState(playbackState, it.playWhenReady) }
      }

      override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
      ) {
        when (reason) {
          Player.DISCONTINUITY_REASON_SEEK -> {
            // If they seek while paused, this is how we know the seek is complete
            if (collector.muxPlayerState == MuxPlayerState.PAUSED
              // Seeks on audio-only media are reported this way instead
              || !collector.mediaHasVideoTrack
            ) {
              collector.seeked(false)
            }
          }
          else -> {} // ignored
        }
      }

      override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        timeline.takeIf { it.windowCount > 0 }?.let { tl ->
          val window = Timeline.Window().apply { tl.getWindow(0, this) }
          collector.sourceDurationMs = window.durationMs
        }
      }

      override fun onTracksChanged(
        trackGroups: TrackGroupArray,
        trackSelections: TrackSelectionArray
      ) {
        player?.watchContentPosition(collector)
      }
    } // object : Player.Listener
} // class BaseExoMetrics

/**
 * Generates a player binding for core exoplayer metrics. Data comes from
 *
 * NOTE: This is only used on ExoPlayer versions 2.15 and below. AnalyticsListener is preferred,
 * and its presence is guaranteed on our player object in exo 2.16 and higher
 */
@JvmSynthetic
internal fun exoMetricsFromListener(): MuxPlayerAdapter.PlayerBinding<ExoPlayer> =
  BasicExoMetrics14to15();
