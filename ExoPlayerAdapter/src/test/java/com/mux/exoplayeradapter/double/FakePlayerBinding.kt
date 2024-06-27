package com.mux.exoplayeradapter.double

import com.mux.exoplayeradapter.log
import com.mux.stats.sdk.muxstats.exoplayeradapter.MuxPlayerAdapter
import com.mux.stats.sdk.muxstats.MuxStateCollectorBase
import com.mux.stats.sdk.muxstats.exoplayeradapter.internal.logTag

class FakePlayerBinding<Player>(val name: String) : MuxPlayerAdapter.PlayerBinding<Player> {
  override fun bindPlayer(player: Player, collector:  MuxStateCollectorBase) {
    log(logTag(), "Binding $name: bindPlayer() called")
  }

  override fun unbindPlayer(player: Player, collector:  MuxStateCollectorBase) {
    log(logTag(), "Binding $name: unbindPlayer() called")
  }
}