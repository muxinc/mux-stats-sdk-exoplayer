package com.mux.stats.sdk.muxstats

/**
 * Player states as seen by Mux Data for the purpose of generating metrics
 */
@Suppress("SpellCheckingInspection")
enum class MuxPlayerState {
  BUFFERING,
  REBUFFERING,
  SEEKING,
  SEEKED,
  ERROR,
  PAUSED,
  PLAY,
  PLAYING,
  PLAYING_ADS,
  FINISHED_PLAYING_ADS,
  INIT,
  ENDED
}
