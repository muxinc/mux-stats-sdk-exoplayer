package com.mux.stats.sdk.muxstats

/**
 * Binding for Basic Metrics. Delivers/listens for basic metrics, collectable by just about any
 * player. These metrics include basic play state, play position, errors, etc
 */
abstract class MuxBasicExoMetrics<Player>(player: Player, collector: MuxDataCollector)
  : MuxPlayerBinding<Player>(player, collector) {

}
