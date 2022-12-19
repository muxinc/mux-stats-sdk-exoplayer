package com.mux.stats.sdk.muxstats.internal

import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaItem.LocalConfiguration
import com.google.android.exoplayer2.MediaItem.PlaybackProperties

@Suppress("DEPRECATION")
@JvmSynthetic
internal fun MediaItem.getMediaUrl(): String? {
  // the fields are coming from java so they *are* optional
  val localConfig: LocalConfiguration? = localConfiguration
  val playbackProps: PlaybackProperties? = playbackProperties
  return if (localConfig != null) {
    @Suppress("UNNECESSARY_SAFE_CALL")
    localConfig.uri?.toString()
  } else if (playbackProps != null) {
    @Suppress("UNNECESSARY_SAFE_CALL")
    playbackProps.uri?.toString()
  } else {
    return null
  }
}
