package com.mux.stats.sdk.muxstats.internal

import com.mux.stats.sdk.core.events.IEvent
import com.mux.stats.sdk.core.events.IEventListener

class SimpleEventListener(val dispatcher: (IEvent) -> Unit) : IEventListener {
  private var id: Int = 0

  override fun setId(id: Int) {
    this.id = id
  }

  override fun handle(ev: IEvent?) {
    ev?.let { dispatcher(it) }
  }

  override fun getId(): Int = id

  override fun flush() {}
}
