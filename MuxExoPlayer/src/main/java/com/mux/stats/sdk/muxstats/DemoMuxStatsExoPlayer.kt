package com.mux.stats.sdk.muxstats

import android.app.Activity
import android.content.Context
import android.view.View
import com.google.ads.interactivemedia.v3.api.AdsLoader
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.mux.stats.sdk.core.CustomOptions
import com.mux.stats.sdk.core.MuxSDKViewOrientation
import com.mux.stats.sdk.core.events.EventBus
import com.mux.stats.sdk.core.model.CustomerData
import com.mux.stats.sdk.core.model.CustomerVideoData
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.createExoPlayerAdapter
import com.mux.stats.sdk.muxstats.internal.downcast
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
  // TODO: declare constructor so we can add @JvmOverloads? No, don't.
  //  We can do fun ExoPlayer.monitorWithMuxData() // figures out some of the context/activity/view/whatever
  //  And those extensions can be overloaded for different reasons (ie, only supplying some Customer Data, etc)
  //  *Those* can have non-optional parameters. Make them @JvmSynthetic for maximum safety
  // TODO: All public ctor fields should be optional. A private ctor can assert nullness on anything
  //  that can't be null, using the available data, and provide a more-friendly error message
  // TODO: We might want to add the deprecated ctors, but they've been deprecated since forever,
  //  so why don't we just do a major rev and finally remove them

  private var _player by weak(player)
  private var _playerView by weak(playerView)

  private val muxStats =
    MuxStats(ExoPlayerDelegate { playerAdapter }, playerName, customerData, customOptions)
  private val eventBus = EventBus().apply { addListener(muxStats) }
  private val collector = MuxStateCollector(muxStats, eventBus)
  private val playerAdapter: MuxPlayerAdapter<View, ExoPlayer, ExoPlayer> =
    muxStats.createExoPlayerAdapter(
      activity = context as Activity, // TODO: handle non-activity case
      playerView = playerView,
      player = player,
      eventBus = eventBus
    )

  // todo: em - We may wish to
  private val adsImaSdkListener: AdsImaSDKListener? by lazy {
    AdsImaSDKListener.createIfImaAvailable(
      collector,
      eventBus
    )
  }

  /**
   * The view being used by the player that is being monitored, safely cast as a PlayerView if
   * possible. Null if there is no view or the view is not the right type
   */
  var exoPlayerView: PlayerView? by downcast(::_playerView)

  /**
   * The view being used by the player that is being monitored, safely cast as a StyledPlayerView if
   * possible. Null if there is no view or the view is not the right type
   */
  var styledPlayerView: StyledPlayerView? by downcast(::_playerView)

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

  /**
   * Get the instance of the IMA SDK Listener for tracking ads running through Google's IMA SDK
   * within your application.
   *
   * @return the IMA SDK Listener
   * @throws
   */
  @Deprecated(
    """This method is no longer the preferred method to track Ad performance with Google's
    IMA SDK.
    <p> Use {@link MuxBaseExoPlayer#monitorImaAdsLoader(AdsLoader)} instead."""
  )
  fun getIMASdkListener(): AdsImaSDKListener? {
    return try {
      // Let's just check one of them
      Class.forName("com.google.ads.interactivemedia.v3.api.Ad")
      Class.forName("com.google.ads.interactivemedia.v3.api.AdErrorEvent")
      Class.forName("com.google.ads.interactivemedia.v3.api.AdEvent")
      AdsImaSDKListener(this)
    } catch (cnfe: ClassNotFoundException) {
      throw IllegalStateException("IMA SDK Modules not found")
    }
  }

  /**
   * Monitor an instance of Google IMA SDK's AdsLoader
   *
   * @param adsLoader For ExoPlayer 2.12 AdsLoader is initialized only when the add is requested,
   * this makes this method impossible to use.
   */
  fun monitorImaAdsLoader(adsLoader: AdsLoader?) {
    if (adsLoader == null) {
      MuxLogger.d(MuxBaseExoPlayer.TAG, "Null AdsLoader provided to monitorImaAdsLoader")
      return
    }
    try {
      // TODO: these may not be necessary, but doing it for the sake of it
      Class.forName("com.google.ads.interactivemedia.v3.api.AdsLoader")
      Class.forName("com.google.ads.interactivemedia.v3.api.AdsManager")
      Class.forName("com.google.ads.interactivemedia.v3.api.AdErrorEvent")
      Class.forName("com.google.ads.interactivemedia.v3.api.AdEvent")
      Class.forName("com.google.ads.interactivemedia.v3.api.Ad")
      val baseExoPlayer: MuxBaseExoPlayer = this
      adsLoader.addAdsLoadedListener(AdsLoader.AdsLoadedListener { adsManagerLoadedEvent -> // TODO: Add in the adresponse stuff when we can

        // Set up the ad events that we want to use
        val adsManager = adsManagerLoadedEvent.adsManager

        // Attach mux event and error event listeners.
        adsManager.addAdErrorListener(adsImaSdkListener)
        adsManager.addAdEventListener(adsImaSdkListener)
      } // TODO: probably need to handle some cleanup and things, like removing listeners on destroy
      )
    } catch (cnfe: ClassNotFoundException) {
      return
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
    playerAdapter.unbindEverything()
    muxStats.release()
  }
}

private class ExoPlayerDelegate(val playerAdapter: () -> MuxPlayerAdapter<*, *, *>) :
  IPlayerListener {
  private val viewDelegate: MuxUiDelegate<*>
    get() = playerAdapter().uiDelegate
  private val collector get() = playerAdapter().collector

  init {
    MuxLogger.d(
      "DemoMuxStatsExoPlayer",
      "Creating Delegate with viewD: $viewDelegate \n\tand collector: $collector"
    )
  }

  override fun getCurrentPosition(): Long = collector.playbackPositionMills
    .also {
      MuxLogger.d(
        "DemoMuxStatsExoPlayer", "GetCurrentPosition():\n\t " +
                "viewD: $viewDelegate \n\tand collector: $collector"
      )
    }

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

  override fun getPlayerViewWidth() = viewDelegate.getPlayerViewSize().x

  override fun getPlayerViewHeight() = viewDelegate.getPlayerViewSize().y

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
