package com.mux.stats.sdk.muxstats

import com.mux.stats.sdk.muxstats.internal.observableWeak
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Adapts a player framework to a {@link MuxDataPlayer}, passing events between them
 */
class MuxPlayerAdapter<PlayerView, MainPlayer, ExtraPlayer>(
        player: MainPlayer,
        @Suppress("MemberVisibilityCanBePrivate")
        val muxStats: MuxStats,
        @Suppress("MemberVisibilityCanBePrivate")
        val uiDelegate: MuxUiDelegate<PlayerView>,
        @Suppress("MemberVisibilityCanBePrivate")
        val basicMetrics: PlayerBinding<MainPlayer>,
        @Suppress("MemberVisibilityCanBePrivate")
        val extraMetrics: ExtraPlayerBindings<ExtraPlayer>? = null,
) {

  /**
   * The main Player being observed by this Adapter. When changed, the old player will be unbound
   * and the new player will be bound
   * This is the Player that belongs to {@link #basicMetrics}
   */
  var basicPlayer: MainPlayer? by observableWeak(player) { changeBasicPlayer(it, collector) }

  /**
   * The Player responsible for gathering extra metrics that might not be available from all players
   * such as Bandwidth or Live Latency
   */
  var extraPlayer: ExtraPlayer? by observableWeak(extraMetrics?.player) {
    changeExtraPlayer(it, collector)
  }

  /**
   * The View being used to collect data related to the player view. This is the View being managed
   * by the {@link #uiDelegate}
   */
  var playerView: PlayerView? by uiDelegate::view

  private val collector = MuxDataCollector(muxStats, uiDelegate)

  private fun changeBasicPlayer(player: MainPlayer?, collector: MuxDataCollector) {
    basicPlayer?.let { oldPlayer -> basicMetrics.unbindPlayer(oldPlayer) }
    player?.let { newPlayer -> basicMetrics.bindPlayer(newPlayer, collector) }
  }

  private fun changeExtraPlayer(player: ExtraPlayer?, collector: MuxDataCollector) {
    if (extraMetrics != null) {
      extraPlayer?.let { oldPlayer ->
        extraMetrics.bindings.onEach { it.unbindPlayer(oldPlayer) }
      }
      player?.let { newPlayer ->
        extraMetrics.bindings.onEach { it.bindPlayer(newPlayer, collector) }
      }
      extraMetrics.player = player
    }
  }

  /*
  * A Binding between some Player object and a MuxDataCollector
  */
  interface PlayerBinding<Player> {

    /**
     * Binds a player to a MuxDataCollector, setting listeners or whatever is required to observe
     * state, and calling hooks on MuxDataCollector
     */
    fun bindPlayer(player: Player, collector: MuxDataCollector)

    /**
     * Unbinds a player from a MuxDataCollector, removing listeners and cleaning up
     */
    fun unbindPlayer(player: Player)
  }

  /**
   * Container for bindings that gather metrics that may not be available from all players, such as
   * session data, or bandwidth metrics. These may come from a different source object (ie an
   * internal ExoPlayer) than the main player.
   */
  class ExtraPlayerBindings<ExtraPlayer>(
          player: ExtraPlayer,
          val bindings: List<PlayerBinding<ExtraPlayer>>) {
    var player: ExtraPlayer? by weak(player)
  }
}