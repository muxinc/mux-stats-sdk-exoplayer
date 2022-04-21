package com.mux.stats.sdk.muxstats

import android.view.View
import android.widget.TextView
import com.mux.stats.sdk.muxstats.internal.downcast
import com.mux.stats.sdk.muxstats.internal.weak
import kotlin.properties.Delegates

/**
 * Adapts a player framework to a {@link MuxDataPlayer}, passing events between them
 */
class MuxPlayerAdapter<PlayerView : View, Player>(
        muxStats: MuxStats,
        playerDataSource: PlayerDataSource<Player>,
        @Suppress("MemberVisibilityCanBePrivate")
        val uiDelegate: MuxUiDelegate<PlayerView>,
) {

  /**
   * The Player Binding associated with this Adapter. If the value is changed, the new binding will
   * be used.
   */
  var playerDataSource: PlayerDataSource<Player>? by Delegates.observable(playerDataSource) {
    _, _, new -> changePlayer(new)
  }

  private var player by playerDataSource::player
  private val collector = MuxDataCollector(muxStats)
  // TODO: Something like this in ExoPlayer Adapter or MuxBaseExoPlayer
  private var exoPlayerView: TextView? by downcast(uiDelegate::view2)

  /**
   * Bind this Adapter to a new Player, registering listeners etc
   */
  private fun bindPlayer(player: Player) {}

  /**
   * Unbind from a player, clearing listeners etc
   */
  private fun unbindPlayer(player: Player) {}

  /**
   * Switches the Data Source for the given player
   */
  private fun changePlayer(player: PlayerDataSource<Player>?) {
    this.player?.let { unbindPlayer(it) }
    player?.player?.let {
      bindPlayer(it)
    }
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
     * The Player being wrapped by this object
     */
    var player by weak<Player>()

    /**
     * True when playing a livestream, false otherwise
     */
    abstract val isLive: Boolean
  }
}
