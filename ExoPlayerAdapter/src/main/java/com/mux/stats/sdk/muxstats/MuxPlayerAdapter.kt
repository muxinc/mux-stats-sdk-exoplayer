package com.mux.stats.sdk.muxstats

import android.content.Context
import android.view.View
import com.mux.stats.sdk.muxstats.internal.Weak

/**
 * Adapts a player framework to a {@link MuxDataPlayer}, passing events between them
 */
abstract class MuxPlayerAdapter<PlayerView : View, Player>(
        context: Context,
        muxStats: MuxStats,
        player: Player? = null,
        @Suppress("MemberVisibilityCanBePrivate")
        protected val uiDelegate: MuxUiDelegate<PlayerView>,
) {

  /**
   * Sets the Player View associated with this Adapter
   */
  var playerView: PlayerView?
    get() = uiDelegate.view
    set(value) {
      uiDelegate.view = value
    }

  /**
   * The player being adapter by this object
   */
  var player by Weak(player).onSet { player -> player?.let { changePlayer(it) } }

  internal val collector = MuxDataCollector(muxStats)

  /**
   * Bind this Adapter to a new Player, registering listeners etc
   */
  protected abstract fun bindPlayer(player: Player)

  /**
   * Unbind from a player, clearing listeners etc
   */
  protected abstract fun unbindPlayer(player: Player)

  private fun changePlayer(player: Player) {
    unbindPlayer(player)
    this.player = player
    bindPlayer(player)
  }
}
