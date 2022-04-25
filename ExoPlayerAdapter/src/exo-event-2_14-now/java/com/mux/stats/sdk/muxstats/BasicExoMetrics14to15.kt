package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.mux.stats.sdk.muxstats.internal.handleExoPlaybackState
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Player binding for basic ExoPlayer metrics.
 * This implementation works from 2.14.1 to now (as of 4/22/22)
 *
 * NOTE: This is only used on ExoPlayer versions 2.15 and below. AnalyticsListener is preferred,
 * and its presence is guaranteed on our player object in exo 2.16 and higher
 */
private class BasicExoMetrics14to15 : MuxPlayerAdapter.PlayerBinding<Player> {

  private var playerListener: Player.Listener? = null

  override fun bindPlayer(player: Player, collector: MuxPlayerStateTracker) {
    playerListener = newListener({ player }, collector)
    // non-null guaranteed unless unbind() and bind() on different threads
    player.addListener(playerListener!!)
  }

  override fun unbindPlayer(player: Player) {
    playerListener?.let { player.removeListener(it) }
  }

  private fun newListener(playerSrc: () -> Player, collector: MuxPlayerStateTracker) =
    object : Player.Listener {
      val player by weak(playerSrc()) // player should be weakly reachable in case user doesn't clean up

      override fun onPlaybackStateChanged(playbackState: Int) {
        // We rely on the player's playWhenReady because the order of this callback and its callback
        //  is not well-defined
        player?.let { collector.handleExoPlaybackState(playbackState, it.playWhenReady) }
      }

      // TODO: Test 2.14 variant to ensure this one is available
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

      // TODO: This method is not available until 2.16. On 2.16+ we use AnalyticsListener instead
      // TODO (really big): We only get tracks data if you're not using SimpleExoPlayer, because
      //    the listeners are mutually exclusive.
      //    As written (on master), Passing in SimpleExoPlayer means only using AnalyticsListener,
      //    means not getting tracks data, not starting the playback pos watcher, not collecting any
      //    data unless it can be gotten from the analytics listener, which may or may not be
      //    listening for it
//      override fun onTracksInfoChanged(tracksInfo: TracksInfo) {
//      }
    } // object : Player.Listener
} // class BaseExoMetrics

/**
 * Generates a player binding for core exoplayer metrics. Data comes from
 *
 * NOTE: This is only used on ExoPlayer versions 2.15 and below. AnalyticsListener is preferred,
 * and its presence is guaranteed on our player object in exo 2.16 and higher
 */
@JvmSynthetic
internal fun exoMetricsFromListener(): MuxPlayerAdapter.PlayerBinding<Player> = BasicExoMetrics14to15();
