package com.mux.stats.sdk.muxstats

/**
 * Collects events from a player and delivers them into a MuxStats instance
 */
class MuxDataCollector(val muxStats: MuxStats) {

  /**
   * The current state of the player, as represented by Mux Data
   */
  val playerState by ::_playerState
  private var _playerState: MuxPlayerState = MuxPlayerState.INIT

  // TODO: Add a class that implements IPlayerListener to read from the delegates
}
