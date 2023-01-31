package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem

/*
 * # ExoPlayerExt.kt: Useful extensions on [ExoPlayer].
 */

/**
 * Gets the Ad Tag URL for the [AdsConfiguration] associated with this [MediaItem], if any
 *
 * Returns null if there's no ads configured, or no media item set
 */
fun ExoPlayer.getAdTagUrl(): String? =
  currentMediaItem?.playbackProperties?.adsConfiguration?.adTagUri?.toString()
