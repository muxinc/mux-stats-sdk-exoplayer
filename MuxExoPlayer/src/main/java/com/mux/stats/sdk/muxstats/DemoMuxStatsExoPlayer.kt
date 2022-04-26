package com.mux.stats.sdk.muxstats

import android.app.Activity
import android.content.Context
import android.view.View
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.mux.stats.sdk.core.CustomOptions
import com.mux.stats.sdk.core.events.EventBus
import com.mux.stats.sdk.core.model.CustomerData
import com.mux.stats.sdk.muxstats.internal.createExoPlayerAdapter
import com.mux.stats.sdk.muxstats.internal.weak

@Suppress("unused")
class DemoMuxStatsExoPlayer(
  context: Context,
  player: ExoPlayer,
  playerView: View? = null,
  playerName: String,
  customerData: CustomerData,
  customOptions: CustomOptions? = null,
  network: INetworkRequest = MuxNetworkRequests()
) {

  private var _player by weak(player)

  private val muxStats = MuxStats(null, playerName, customerData, customOptions)
  private val eventBus = EventBus().apply { addListener(muxStats) }
  private val collector =
    MuxPlayerStateTracker(muxStats, eventBus)
  private val playerAdapter = muxStats.createExoPlayerAdapter(
    activity = context as Activity, // TODO: handle non-activity case
    playerView = playerView,
    player = player,
    eventBus = eventBus
  )

  init {
    // Catch up to the current playback state if we start monitoring in the middle of play
    if (player.playbackState == Player.STATE_BUFFERING) {
      // playback started before muxStats was initialized
      collector.play()
      collector.buffering()
    } else if (player.playbackState == Player.STATE_READY) {
      // We have to simulate all the events we expect to see here, even though not ideal
      collector.play()
      collector.buffering()
      collector.playing()
    }

    /**
     * Tears down this object. After this, the object will no longer be usable
     */
    fun release() {
      playerAdapter.release()
      muxStats.release()
    }
  }
}
