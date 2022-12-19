package com.mux.stats.sdk.muxstats.internal

import com.google.android.exoplayer2.MediaItem

@JvmSynthetic
internal fun MediaItem.getMediaUrl(): String? {
  return playbackProperties?.uri?.toString()
}
