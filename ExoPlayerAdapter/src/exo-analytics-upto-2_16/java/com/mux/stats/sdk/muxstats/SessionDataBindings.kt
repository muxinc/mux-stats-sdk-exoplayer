package com.mux.stats.sdk.muxstats

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.source.hls.HlsManifest
import com.mux.stats.sdk.core.model.SessionTag
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.isHlsExtensionAvailable
import com.mux.stats.sdk.muxstats.internal.weak
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Session Data player binding for Exo versions 2.16 and below. Requires a SimpleExoPlayer otherwise
 * [bindPlayer] and [unbindPlayer] are no-ops
 */
private class SessionDataPlayerBinding : MuxPlayerAdapter.PlayerBinding<ExoPlayer> {

  private var listener: AnalyticsListener? by weak(null)

  override fun bindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    if (isHlsExtensionAvailable() && player is SimpleExoPlayer) {
      listener = SessionDataListener(player, collector).also { player.addAnalyticsListener(it) }
    }
  }

  override fun unbindPlayer(player: ExoPlayer, collector: MuxStateCollector) {
    if (player is SimpleExoPlayer) {
      listener?.let { player.removeAnalyticsListener(it) }
    }
  }

  /**
   * Listens for timeline changes and updates HLS session data if we're on an HLS stream.
   * This class should only be instantiated if ExoPlayer's HLS extension is available at runtime
   * @see [.isHlsExtensionAvailable]
   */
  private class SessionDataListener(player: ExoPlayer, val collector: MuxStateCollector) :
    AnalyticsListener {

    private val player by weak(player)

    companion object {
      val RX_SESSION_TAG_DATA_ID by lazy { Pattern.compile("DATA-ID=\"(.*)\",") }
      val RX_SESSION_TAG_VALUES by lazy { Pattern.compile("VALUE=\"(.*)\"") }

      /** HLS session data tags with this Data ID will be sent to Mux Data  */
      const val HLS_SESSION_LITIX_PREFIX = "io.litix.data."
      const val LOG_TAG = "SessionDataListener"
    }

    override fun onTimelineChanged(eventTime: AnalyticsListener.EventTime, reason: Int) {
      player?.let { safePlayer ->
        val manifest = safePlayer.currentManifest
        if (manifest is HlsManifest) {
          collector.onMainPlaylistTags(parseHlsSessionData(manifest.masterPlaylist.tags))
        }
      }
    }

    private fun parseHlsSessionData(hlsTags: List<String>): List<SessionTag> {
      val data: MutableList<SessionTag> = ArrayList()
      for (tag in filterHlsSessionTags(hlsTags)) {
        val st: SessionTag = parseHlsSessionTag(tag)
        if (st.key != null && st.key.contains(HLS_SESSION_LITIX_PREFIX)) {
          data.add(parseHlsSessionTag(tag))
        }
      }
      return data
    }

    private fun filterHlsSessionTags(rawTags: List<String>) =
      rawTags.filter { it.substring(1).startsWith("EXT-X-SESSION-DATA") }

    private fun parseHlsSessionTag(line: String): SessionTag {
      val dataId: Matcher = RX_SESSION_TAG_DATA_ID.matcher(line)
      val value: Matcher = RX_SESSION_TAG_VALUES.matcher(line)
      var parsedDataId: String? = ""
      var parsedValue: String? = ""
      if (dataId.find()) {
        parsedDataId = dataId.group(1)?.replace(HLS_SESSION_LITIX_PREFIX, "")
      } else {
        MuxLogger.d(LOG_TAG, "Data-ID not found in session data: $line")
      }
      if (value.find()) {
        parsedValue = value.group(1)
      } else {
        MuxLogger.d(LOG_TAG, "Value not found in session data: $line")
      }
      return SessionTag(parsedDataId, parsedValue)
    }
  }
}

/**
 * Creates a listener that listens for timeline changes and updates HLS session data if we're on an
 * HLS stream.
 * This class should only be instantiated if ExoPlayer's HLS extension is available at runtime
 * @see [.isHlsExtensionAvailable]
 */
@Suppress("unused") // using the receiver to avoid polluting customers' namespace
@JvmSynthetic
fun MuxStateCollector.createExoSessionDataBinding(): MuxPlayerAdapter.PlayerBinding<ExoPlayer> =
  SessionDataPlayerBinding()
