package com.mux.stats.sdk.muxstats.internal

import android.app.Activity
import android.view.View
import com.google.android.exoplayer2.ExoPlayer
import com.mux.stats.sdk.core.events.EventBus
import com.mux.stats.sdk.muxstats.*

/**
 * Generates PlayerBindings for ExoPlayer
 */
private class BasicExoPlayerBindings : MuxPlayerAdapter.PlayerBinding<ExoPlayer> {
  private val coreBinding = playerStateMetrics()
  private val errorBinding = playerErrorMetrics()

  override fun bindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    coreBinding.bindPlayer(player, collector)
  }

  override fun unbindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    coreBinding.bindPlayer(player, collector)
  }
}

/**
 * Creates a new PlayerAdapter that monitors an ExoPlayer
 */
@Suppress("unused")
fun MuxStats.createExoPlayerAdapter(
  activity: Activity,
  playerView: View?,
  player: ExoPlayer,
  eventBus: EventBus
): MuxPlayerAdapter<View, ExoPlayer, ExoPlayer> = MuxPlayerAdapter(
  player = player,
  uiDelegate = playerView.muxUiDelegate(activity),
  basicMetrics = BasicExoPlayerBindings(),
  collector = MuxStateCollector(
    muxStats = this,
    dispatcher = eventBus
  )
)

