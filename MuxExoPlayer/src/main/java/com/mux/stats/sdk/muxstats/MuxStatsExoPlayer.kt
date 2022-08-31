package com.mux.stats.sdk.muxstats

import android.content.Context
import android.view.View
import com.google.ads.interactivemedia.v3.api.AdsLoader
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.mux.stats.sdk.core.Core
import com.mux.stats.sdk.core.CustomOptions
import com.mux.stats.sdk.core.MuxSDKViewOrientation
import com.mux.stats.sdk.core.events.EventBus
import com.mux.stats.sdk.core.events.IEvent
import com.mux.stats.sdk.core.model.CustomerData
import com.mux.stats.sdk.core.model.CustomerVideoData
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.internal.*
import com.mux.stats.sdk.muxstats.internal.weak

@Suppress("unused")
class MuxStatsExoPlayer(
  val context: Context,
  player: ExoPlayer,
  playerView: View? = null,
  playerName: String,
  val customerData: CustomerData,
  customOptions: CustomOptions? = null,
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

  companion object {
    const val TAG = "MuxStatsExoPlayer"
  }

  private var _player by weak(player)
  private var _playerView by weak(playerView)

  private val eventBus = EventBus()//.apply { addListener(muxStats) }
  private val collector = MuxStateCollector({ muxStats }, eventBus)
  private val playerAdapter = collector.createExoPlayerAdapter(
    context = context,
    playerView = playerView,
    player = player,
  )
  private val muxStats: MuxStats // Set in init{} because INetworkRequest must be set statically 1st

  val imaSdkListener: AdsImaSDKListener? by lazy {
    AdsImaSDKListener.createIfImaAvailable(
      player,
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
  // TODO need to be splitted by Exo variants
//  var styledPlayerView: StyledPlayerView? by downcast(::_playerView)

  init {
    // Init MuxStats (muxStats must be created last)
    MuxStats.setHostDevice(MuxBaseExoPlayer.MuxDevice(context))
    MuxStats.setHostNetworkApi(network)
    muxStats =
      MuxStats(ExoPlayerDelegate(), playerName, customerData, customOptions ?: CustomOptions())
        .also { eventBus.addListener(it) }

    // Setup logging for debug builds of the SDK
    enableMuxCoreDebug(isDebugVariant(), false)
    Core.allowLogcatOutputForPlayer(playerName, isDebugVariant(), false)

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
    // We always need these two values.
    collector.allowHeaderToBeSentToBackend("x-cdn")
    collector.allowHeaderToBeSentToBackend("content-type")
  }

  /**
   * Get the instance of the IMA SDK Listener for tracking ads running through Google's IMA SDK
   * within your application.
   *
   * @return the IMA SDK Listener
   */
  @Deprecated(
    """This method is no longer the preferred method to track Ad performance with Google's
    IMA SDK.
    <p> Use {@link MuxBaseExoPlayer#monitorImaAdsLoader(AdsLoader)} instead."""
  )
  fun getAdsImaSdkListener(): AdsImaSDKListener? = imaSdkListener

  /**
   * Monitor an instance of Google IMA SDK's AdsLoader
   *
   * @param adsLoader For ExoPlayer 2.12 AdsLoader is initialized only when the add is requested,
   * this makes this method impossible to use.
   */
  fun monitorImaAdsLoader(adsLoader: AdsLoader?) {
    if (adsLoader == null) {
      MuxLogger.d(logTag(), "Null AdsLoader provided to monitorImaAdsLoader")
      return
    }
    try {
      // TODO: these may not be necessary, but doing it for the sake of it
      adsLoader.addAdsLoadedListener(AdsLoader.AdsLoadedListener { adsManagerLoadedEvent -> // TODO: Add in the adresponse stuff when we can
        // Set up the ad events that we want to use
        val adsManager = adsManagerLoadedEvent.adsManager
        // Attach mux event and error event listeners.
        adsManager.addAdErrorListener(imaSdkListener)
        adsManager.addAdEventListener(imaSdkListener)
      } // TODO: probably need to handle some cleanup and things, like removing listeners on destroy
      )
    } catch (cnfe: ClassNotFoundException) {
      return
    }
  }

  fun isPaused() = playerAdapter.collector.isPaused()

  fun setAutomaticErrorTracking(enabled: Boolean) = muxStats.setAutomaticErrorTracking(enabled)

  fun setPlayerView(view: View?) {
    playerAdapter.playerView = view
  }

  fun setPlayerSize(width: Int, height: Int) = muxStats.setPlayerSize(width, height)

  fun setScreenSize(width: Int, height: Int) = muxStats.setScreenSize(width, height)

  fun videoChange(videoData: CustomerVideoData) {
    collector.videoChange(videoData)
  }

  fun programChange(videoData: CustomerVideoData) = collector.programChange(videoData)

  fun orientationChange(orientation: MuxSDKViewOrientation) =
    muxStats.orientationChange(orientation)

  fun presentationChange(presentation: MuxSDKViewPresentation) =
    muxStats.presentationChange(presentation)

  fun dispatch(event: IEvent?) = eventBus.dispatch(event)

  /**
   * Allow HTTP headers with a given name to be passed to the backend. By default we ignore all HTTP
   * headers that are not in the [BandwidthMetricDispatcher.allowedHeaders] list.
   * This is used in automated tests and is not intended to be used from the application layer.
   *
   * @param headerName name of the header to send to the backend.
   */
  protected fun allowHeaderToBeSentToBackend(headerName: String?) {
    collector.allowHeaderToBeSentToBackend(headerName)
  }

  fun enableMuxCoreDebug(enable: Boolean, verbose: Boolean) =
    muxStats.allowLogcatOutput(enable, verbose)

  /**
   * Tears down this object. After this, the object will no longer be usable
   */
  fun release() {
    playerAdapter.unbindEverything()
    muxStats.release()
  }

  /**
   * Convert physical pixels to device density independent pixels.
   *
   * @param px physical pixels to be converted.
   * @return number of density pixels calculated.
   */
  fun pxToDp(px: Int): Int {
    val displayMetrics = context.resources.displayMetrics
    return Math.ceil((px / displayMetrics.density).toDouble()).toInt()
  }

  private inner class ExoPlayerDelegate : IPlayerListener {
    private val viewDelegate: MuxUiDelegate<*> get() = playerAdapter.uiDelegate

    override fun getCurrentPosition(): Long = collector.playbackPositionMills

    override fun getMimeType() = collector.mimeType

    override fun getSourceWidth(): Int = collector.sourceWidth

    override fun getSourceHeight(): Int = collector.sourceHeight

    override fun getSourceAdvertisedBitrate(): Int = collector.sourceAdvertisedBitrate

    override fun getSourceAdvertisedFramerate(): Float = collector.sourceAdvertisedFrameRate

    override fun getSourceDuration() = collector.sourceDurationMs

    override fun isPaused() = collector.isPaused()

    override fun isBuffering(): Boolean = collector.muxPlayerState == MuxPlayerState.BUFFERING

    override fun getPlayerViewWidth() = pxToDp(viewDelegate.getPlayerViewSize().x)

    override fun getPlayerViewHeight() = pxToDp(viewDelegate.getPlayerViewSize().y)

    override fun getPlayerProgramTime(): Long? {
      return collector.currentTimelineWindow.windowStartTimeMs + currentPosition
    }

    override fun getPlayerManifestNewestTime(): Long? {
      return if (collector.isLivePlayback()) {
        collector.currentTimelineWindow.windowStartTimeMs
      } else -1L
    }

    override fun getVideoHoldback(): Long? {
      return if (collector.isLivePlayback())
        collector.parseManifestTagL("HOLD-BACK") else -1
    }

    override fun getVideoPartHoldback(): Long? {
      return if (collector.isLivePlayback())
        collector.parseManifestTagL("PART-HOLD-BACK") else -1
    }

    override fun getVideoPartTargetDuration(): Long? {
      return if (collector.isLivePlayback())
        collector.parseManifestTagL("PART-TARGET") else -1
    }

    override fun getVideoTargetDuration(): Long? {
      return if (collector.isLivePlayback())
        collector.parseManifestTagL("EXT-X-TARGETDURATION") else -1
    }
  }
}
