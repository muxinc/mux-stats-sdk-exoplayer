package com.mux.stats.sdk.muxstats.internal

import com.mux.stats.sdk.muxstats.BuildConfig
import com.mux.stats.sdk.muxstats.MuxStats

/**
 * Returns true if the object is one of any of the parameters supplied
 */
@JvmSynthetic // Hide from Java callers because all are external
internal fun Any.oneOf(vararg accept: Any) = accept.contains(this)

/**
 * Returns true if the object is not any of the parameters supplied
 */
@JvmSynthetic // Hide from Java callers because all are external
internal fun Any.noneOf(vararg accept: Any) = !accept.contains(this)

/**
 * Gets a Log Tag from the name of the calling class. Can be used in any package that isn't
 * obfuscated (such as muxstats)
 */
internal inline fun <reified T> T.logTag() = T::class.java.simpleName

@JvmSynthetic
internal fun isDebugVariant() = BuildConfig.BUILD_TYPE == "debug"
