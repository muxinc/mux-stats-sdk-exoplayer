package com.mux.stats.sdk.muxstats.exoplayeradapter.internal

import android.app.Activity
import android.content.Context
import android.view.View
import com.google.android.exoplayer2.ExoPlayer
import com.mux.stats.sdk.muxstats.*
import com.mux.stats.sdk.muxstats.exoplayeradapter.MuxPlayerAdapter
import com.mux.stats.sdk.muxstats.exoplayeradapter.MuxUiDelegate
import com.mux.stats.sdk.muxstats.exoplayeradapter.muxUiDelegate
import com.mux.stats.sdk.muxstats.exoplayeradapter.noUiDelegate

/**
 * Generates PlayerBindings for ExoPlayer
 */
private class BasicExoPlayerBindings : MuxPlayerAdapter.PlayerBinding<ExoPlayer> {
  private val coreBinding = playerStateMetrics()
  private val errorBinding = playerErrorMetrics()

  override fun bindPlayer(player: ExoPlayer, collector:  MuxStateCollectorBase) {
    coreBinding.bindPlayer(player, collector)
    errorBinding.bindPlayer(player, collector)
  }

  override fun unbindPlayer(player: ExoPlayer, collector:  MuxStateCollectorBase) {
    coreBinding.unbindPlayer(player, collector)
    errorBinding.unbindPlayer(player, collector)
  }
}

/**
 * Creates a new PlayerAdapter that monitors an ExoPlayer
 */
@Suppress("unused")
fun  MuxStateCollectorBase.createExoPlayerAdapter(
  context: Context,
  playerView: View?,
  player: ExoPlayer,
): MuxPlayerAdapter<View, ExoPlayer, ExoPlayer> = MuxPlayerAdapter(
  player = player,
  uiDelegate = uiDelegate(context, playerView),
  basicMetrics = BasicExoPlayerBindings(),
  collector = this,
  extraMetrics = MuxPlayerAdapter.ExtraPlayerBindings(
    player,
    listOf(
      createExoSessionDataBinding()
    )
  )
)

private fun uiDelegate(context: Context, playerView: View?): MuxUiDelegate<View> {
  return if (context is Activity) {
    playerView.muxUiDelegate(context)
  } else {
    noUiDelegate()
  }
}
