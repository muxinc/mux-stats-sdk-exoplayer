package com.mux.stats.sdk.muxstats.internal

import java.lang.ref.WeakReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Property Delegate where the property's referent is not reachable
 * The implementation is private, but within this module you can use weak(...) to use this class
 *   (this prevents Weak from being instantiated in java with `new Weak$library()`)
 */
private class WeakRef<T>(referent: T?) : ReadWriteProperty<Any, T?> {
  private var weakT = WeakReference(referent)
  private var onSet: ((T?) -> Unit)? = null

  fun onSet(block: (T?) -> Unit): WeakRef<T> {
    onSet = block
    return this
  }

  override fun getValue(thisRef: Any, property: KProperty<*>): T? = weakT.get()

  override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
    value?.let { onSet?.invoke(value) }
    weakT = WeakReference(value)
  }
}

/**
 * Property Delegate that makes the object referenced by the property weakly-reachable
 * Not thread-safe
 */
@JvmSynthetic // synthetic methods are hidden from java, and java has no property delegates
internal fun <T> weak(t: T?): ReadWriteProperty<Any, T?> = WeakRef(t)

/**
 * Property Delegate that makes the object referenced by the property weakly-reachable
 * Not thread-safe
 */
@JvmSynthetic // synthetic methods are hidden from java, and java has no property delegates
internal fun <T> weak(): ReadWriteProperty<Any, T?> = WeakRef(null)

/**
 * Weakly-reachable property delegate that is observable
 */
@JvmSynthetic
internal fun <T> observableWeak(t: T?, block: (T?) -> Unit): ReadWriteProperty<Any, T?> =
  WeakRef(t).onSet(block)

/**
 * Weakly-reachable property delegate that is observable
 */
@JvmSynthetic
internal fun <T> observableWeak(block: (T?) -> Unit): ReadWriteProperty<Any, T?> =
  WeakRef<T>(null).onSet(block)
