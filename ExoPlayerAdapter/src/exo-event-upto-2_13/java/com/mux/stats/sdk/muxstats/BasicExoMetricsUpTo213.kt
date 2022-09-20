package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.*

/**
 * Player binding for basic ExoPlayer metrics.
 * This implementation works on ExoPlayer up to 2.13.0 (as of 4/22/22)
 */
private class BasicExoMetricsUpTo213 : MuxPlayerAdapter.PlayerBinding<ExoPlayer> {

  private var playerListener: Player.EventListener? by weak(null)

  init {
    MuxLogger.d(logTag(), "created");
  }

  override fun bindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    playerListener = newListener(player, collector).also {
      player.addListener(it)
    }
  }

  override fun unbindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    collector.positionWatcher?.stop("player unbound")
    collector.positionWatcher = null
    playerListener?.let { player.removeListener(it) }
  }

  private fun newListener(player: ExoPlayer, collector: MuxStateCollector) =
    PlayerListener(player, collector)
} // class BaseExoMetrics

private class PlayerListener(player: ExoPlayer, val collector: MuxStateCollector) :
  Player.EventListener {
  val player by weak(player) // player should be weakly reachable in case user doesn't clean up

//  override fun onPlaybackStateChanged(playbackState: Int) {
//    // We rely on the player's playWhenReady because the order of this callback and its callback
//    //  is not well-defined
//    player?.let { collector.handleExoPlaybackState(playbackState, it.playWhenReady) }
//  }

  override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
    player?.let { collector.handleExoPlaybackState(playbackState, it.playWhenReady) }
  }

  override fun onPositionDiscontinuity(reason: Int) {
    collector.handlePositionDiscontinuity(reason)
  }

  override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {
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
} // class PlayerListener

/**
 * Generates a player binding for exoplayer error metrics.
 */
@JvmSynthetic
internal fun basicExoEvents(): MuxPlayerAdapter.PlayerBinding<ExoPlayer> =
  BasicExoMetricsUpTo213();
