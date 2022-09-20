package com.mux.stats.sdk.muxstats

import com.mux.stats.sdk.core.events.IEventDispatcher
import com.mux.stats.sdk.muxstats.MuxStateCollectorBase
import com.mux.stats.sdk.muxstats.MuxStats

open class MuxStateCollector(
    private val _muxStats: () -> MuxStats,
    private val _dispatcher: IEventDispatcher,
    private val _trackFirstFrameRendered: Boolean = true,
): MuxStateCollectorBase(_muxStats, _dispatcher, _trackFirstFrameRendered) {
    override fun isLivePlayback(): Boolean {
        return false;
    }

    override fun parseManifestTag(tagName: String): String {
        return "-1"
    }
}