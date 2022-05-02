package com.mux.exoplayeradapter.double

import android.util.Log
import com.mux.stats.sdk.muxstats.MuxPlayerAdapter
import com.mux.stats.sdk.muxstats.MuxStateCollector
import com.mux.stats.sdk.muxstats.internal.logTag

class FakePlayerBinding<Player> : MuxPlayerAdapter.PlayerBinding<Player> {
  override fun bindPlayer(player: Player, collector: MuxStateCollector) {
    Log.v(logTag(), "bindPlayer() called")
  }

  override fun unbindPlayer(player: Player, collector: MuxStateCollector) {
    Log.v(logTag(), "unbindPlayer() called")
  }
}