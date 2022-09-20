package com.mux.exoplayeradapter.double

import com.mux.exoplayeradapter.log
import com.mux.stats.sdk.muxstats.MuxPlayerAdapter
import com.mux.stats.sdk.muxstats.MuxStateCollector
import com.mux.stats.sdk.muxstats.internal.logTag

class FakePlayerBinding<Player>(val name: String) : MuxPlayerAdapter.PlayerBinding<Player> {
  override fun bindPlayer(player: Player, collector: MuxStateCollector) {
    log(logTag(), "Binding $name: bindPlayer() called")
  }

  override fun unbindPlayer(player: Player, collector: MuxStateCollector) {
    log(logTag(), "Binding $name: unbindPlayer() called")
  }
}