package com.mux.stats.sdk.muxstats.internal

import java.lang.ref.WeakReference
import kotlin.reflect.KProperty

/**
 * Property Delegate that makes the object referenced by the property weakly-reachable
 * You an also observe changes with onSet() and onGet()
 * Not thread-safe
 */
class Weak<T>(referent: T?) {
  private var weakT = WeakReference(referent)
  private var onSet: ((T?) -> Unit)? = null
  private var onGet: (() -> Unit)? = null

  operator fun getValue(thisRef: Any, property: KProperty<*>) = weakT.get()

  operator fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
    weakT = WeakReference(value)
  }

  fun onSet(block: (T?) -> Unit): Weak<T> {
    onSet = block
    return this
  }

  fun onGet(block: () -> Unit): Weak<T> {
    onGet = block
    return this
  }
}
