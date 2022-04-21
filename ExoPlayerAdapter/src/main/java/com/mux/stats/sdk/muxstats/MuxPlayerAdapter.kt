package com.mux.stats.sdk.muxstats

import android.view.View
import android.widget.TextView
import com.mux.stats.sdk.muxstats.internal.downcast
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Adapts a player framework to a {@link MuxDataPlayer}, passing events between them
 */
abstract class MuxPlayerAdapter<PlayerView : View, Player>(
        muxStats: MuxStats,
        @Suppress("MemberVisibilityCanBePrivate")
        val playerDataSource: PlayerDataSource<Player>,
        @Suppress("MemberVisibilityCanBePrivate")
        val uiDelegate: MuxUiDelegate<PlayerView>,
) {

  /**
   * The player being adapter by this object.
   */
  protected var player by playerDataSource::player

  protected var exoPlayerView: TextView? by downcast(uiDelegate::view2)

  /**
   * Data collector, which tracks the state of stuff.
   * TODO: Whole Class:
   *  Inputs: Data Src (gets Stuff from player)
   *    Binding/Listener: Forwards callbacks to a Collector.
   *    common line in binding: player.setListener { collector.x(it) }
   *  This class itself does: Binds listeners and data srcs to player, relies on subclasses to
   *    forward/get stuff
   *  What about View-y stuff? We do need a view, but here? Only for dimensions?
   *    The Collector needs the UI delegate.
   */
  protected val collector = MuxDataCollector(muxStats)

  private var _dataSrc: PlayerDataSource<Player>? = null

  /**
   * Bind this Adapter to a new Player, registering listeners etc
   */
  protected abstract fun bindPlayer(player: Player)

  /**
   * Unbind from a player, clearing listeners etc
   */
  protected abstract fun unbindPlayer(player: Player)

  /**
   * Creates the object that pulls data from the Player
   */
  protected abstract fun createDataSource(player: Player): PlayerDataSource<Player>

  /**
   * Switches out the player being monitored by this Adapter
   */
  fun changePlayer(player: Player?) {
    this.player?.let { unbindPlayer(it) }
    player?.let {
      this._dataSrc = createDataSource(it)
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
