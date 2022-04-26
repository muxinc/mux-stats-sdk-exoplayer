package com.mux.stats.sdk.muxstats

import android.app.Activity
import android.content.Context
import android.view.View
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.mux.stats.sdk.core.CustomOptions
import com.mux.stats.sdk.core.MuxSDKViewOrientation
import com.mux.stats.sdk.core.events.EventBus
import com.mux.stats.sdk.core.model.CustomerData
import com.mux.stats.sdk.core.model.CustomerVideoData
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.createExoPlayerAdapter
import com.mux.stats.sdk.muxstats.internal.weak

@Suppress("unused")
class DemoMuxStatsExoPlayer(
  context: Context,
  player: ExoPlayer,
  playerView: View? = null,
  playerName: String,
  val customerData: CustomerData,
  val customOptions: CustomOptions? = null,
  network: INetworkRequest = MuxNetworkRequests()
) {

  private var _player by weak(player)
  private var _playerView by weak(playerView)

  private val muxStats =
    MuxStats(ExoPlayerDelegate { playerAdapter }, playerName, customerData, customOptions)
  private val eventBus = EventBus().apply { addListener(muxStats) }
  private val collector =
    MuxPlayerStateTracker(muxStats, eventBus)
  private val playerAdapter: MuxPlayerAdapter<View, ExoPlayer, ExoPlayer> =
    muxStats.createExoPlayerAdapter(
      activity = context as Activity, // TODO: handle non-activity case
      playerView = playerView,
      player = player,
      eventBus = eventBus
    )

  init {
    // Initialize MuxStats stuff
    MuxStats.setHostDevice(MuxBaseExoPlayer.MuxDevice(context))
    MuxStats.setHostNetworkApi(network)
    // Uncomment for Logcat
    // enableMuxCoreDebug(true, true)

    // Catch up to the current playback state if we start monitoring in the middle of play
    if (player.playbackState == Player.STATE_BUFFERING) {
      // playback started before muxStats was initialized
      collector.play()
      collector.buffering()
    } else if (player.playbackState == Player.STATE_READY) {
      // We have to simulate all the events we expect to see here, even though not ideal
      collector.play()
      collector.buffering()
      collector.playing()
    }
  }

  fun setAutomaticErrorTracking(enabled: Boolean) = muxStats.setAutomaticErrorTracking(enabled)

  fun setPlayerView(view: View?) {
    playerAdapter.playerView = view
  }

  fun setPlayerSize(width: Int, height: Int) = muxStats.setPlayerSize(width, height)

  fun setScreenSize(width: Int, height: Int) = muxStats.setScreenSize(width, height)

  fun videoChange(videoData: CustomerVideoData) {
    collector.videoChange(videoData)
  }

  fun programChange(videoData: CustomerVideoData) {
    collector.programChange(videoData)
  }

  fun orientationChange(orientation: MuxSDKViewOrientation) =
    muxStats.orientationChange(orientation)

  fun presentationChange(presentation: MuxSDKViewPresentation) =
    muxStats.presentationChange(presentation)

  fun enableMuxCoreDebug(enable: Boolean, verbose: Boolean) {
    muxStats.allowLogcatOutput(enable, verbose)
  }

  /**
   * Tears down this object. After this, the object will no longer be usable
   */
  fun release() {
    playerAdapter.release()
    muxStats.release()
  }
}

private class ExoPlayerDelegate(
  playerAdapter: () -> MuxPlayerAdapter<*, *, *>,
) :
  IPlayerListener {
  private val viewDelegate by weak(playerAdapter().uiDelegate)
  private val collector = playerAdapter().collector

  init {
    MuxLogger.d(
      "DemoMuxStatsExoPlayer",
      "Creating Delegate with viewD: $viewDelegate \n\tand collector: $collector"
    )
  }

  override fun getCurrentPosition(): Long = collector.playbackPositionMills

  override fun getMimeType() = collector.mimeType

  override fun getSourceWidth(): Int? = 0//collector.sourceWidth

  override fun getSourceHeight(): Int? = 0//sourceHeight

  override fun getSourceAdvertisedBitrate(): Int {
    TODO("Not yet implemented")
  }

  override fun getSourceAdvertisedFramerate(): Float {
    TODO("Not yet implemented")
  }

  override fun getSourceDuration() = collector.sourceDurationMs

  override fun isPaused() = collector.muxPlayerState == MuxPlayerState.PAUSED

  override fun isBuffering(): Boolean = collector.muxPlayerState == MuxPlayerState.BUFFERING

  override fun getPlayerViewWidth() = viewDelegate?.getPlayerViewSize()?.x ?: 0

  override fun getPlayerViewHeight() = viewDelegate?.getPlayerViewSize()?.y ?: 0

  override fun getPlayerProgramTime(): Long {
    TODO("Not yet implemented")
  }

  override fun getPlayerManifestNewestTime(): Long {
    TODO("Not yet implemented")
  }

  override fun getVideoHoldback(): Long {
    TODO("Not yet implemented")
  }

  override fun getVideoPartHoldback(): Long {
    TODO("Not yet implemented")
  }

  override fun getVideoPartTargetDuration(): Long {
    TODO("Not yet implemented")
  }

  override fun getVideoTargetDuration(): Long {
    TODO("Not yet implemented")
  }


}
