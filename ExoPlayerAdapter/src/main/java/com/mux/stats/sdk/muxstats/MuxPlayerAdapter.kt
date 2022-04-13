package com.mux.stats.sdk.muxstats

import android.view.View
import com.mux.stats.sdk.muxstats.internal.Weak

/**
 * Adapts a player framework to a {@link MuxDataPlayer}, passing events between them
 */
abstract class MuxPlayerAdapter<PlayerView : View, Player>(
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

  protected var playerDataSource: PlayerDataSource<Player>? = null

  /**
   * Bind this Adapter to a new Player, registering listeners etc
   */
  protected abstract fun bindPlayer(player: Player)

  /**
   * Unbind from a player, clearing listeners etc
   */
  protected abstract fun unbindPlayer(player: Player)

  private fun changePlayer(player: Player) {
    this.player?.let { unbindPlayer(it) }
    this.player = player
    this.playerDataSource
    bindPlayer(player)
  }

  /**
   *  Data source for player state data, for {@link MuxDataCollector}. Implementations should call
   *  properties of the player being monitored and forward results
   */
  abstract class PlayerDataSource<Player>() {

    constructor(player: Player) : this() {
      this.player = player
    }

    /**
     * True when playing a livestream, false otherwise
     */
    abstract val isLive: Boolean

    /**
     * The Player being wrapped by this object
     */
    var player by Weak<Player>(null)
  }
}

