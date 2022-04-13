package com.mux.stats.sdk.muxstats

/**
 * Collects events from a player and delivers them into a MuxStats instance
 */
internal class MuxDataCollector(val muxStats: MuxStats) {

  /**
   * The current state of the player, as represented by Mux Data
   */
  val playerState by ::_playerState
  private var _playerState: MuxPlayerState = MuxPlayerState.INIT

}
