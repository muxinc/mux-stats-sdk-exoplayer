package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.source.hls.HlsManifest
import com.mux.stats.sdk.core.events.IEventDispatcher

open class MuxStateCollector(
    private val _muxStats: () -> MuxStats,
    private val _dispatcher: IEventDispatcher,
    private val _trackFirstFrameRendered: Boolean = true,
): MuxStateCollectorBase(_muxStats, _dispatcher, _trackFirstFrameRendered) {
    override fun isLivePlayback(): Boolean {
        return currentTimelineWindow.isLive
    }

    override fun parseManifestTag(tagName: String): String {
        synchronized(currentTimelineWindow) {
            if (currentTimelineWindow != null && currentTimelineWindow.manifest != null && tagName != null && tagName.length > 0
            ) {
                if (currentTimelineWindow.manifest is HlsManifest) {
                    val manifest = currentTimelineWindow.manifest as HlsManifest
                    if (manifest.mediaPlaylist.tags != null) {
                        for (tag in manifest.mediaPlaylist.tags) {
                            if (tag.contains(tagName)) {
                                var value = tag.split(tagName).toTypedArray()[1]
                                if (value.contains(",")) {
                                    value = value.split(",").toTypedArray()[0]
                                }
                                if (value.startsWith("=") || value.startsWith(":")) {
                                    value = value.substring(1, value.length)
                                }
                                return value
                            }
                        }
                    }
                }
            }
        }
        return "-1"
    }
}