package com.mux.stats.sdk.muxstats.internal

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

/**
 * Property Delegate that down-casts a value from a delegated property
 * The Parameters are the upper & lower bounds
 * Access this class via the helper functions below
 */
private class Downcast<Upper, Lower : Upper>(var t: KMutableProperty0<Upper?>)
  : ReadWriteProperty<Any, Lower?> {

  override fun getValue(thisRef: Any, property: KProperty<*>): Lower? {
    @Suppress("UNCHECKED_CAST") // Safety guaranteed by compiler
    return t.get() as? Lower
  }

  override fun setValue(thisRef: Any, property: KProperty<*>, value: Lower?) {
    t.set(value)
  }
}

/**
 * Property Delegate that down-casts the value of another property. Another Property must be
 * supplied, using the `::` operator, or some other method.
 * The Property must be nullable.
 *
 * Example usage:
 *  // where aView is a View or other superclass of ExoPlayerView:
 *  var exoPlayerView: ExoPlayerView by downcast<View, ExoPlayerView>(anObject::aView)
 */
@JvmSynthetic // Hide from Java, since Property Delegates are useless to Java
internal fun <U, L : U> downcast(delegatedProperty: KMutableProperty0<U?>)
        : ReadWriteProperty<Any, L?> = Downcast(delegatedProperty)
