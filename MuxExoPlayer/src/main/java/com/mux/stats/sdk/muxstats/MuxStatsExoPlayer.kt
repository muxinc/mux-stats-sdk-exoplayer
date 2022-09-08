package com.mux.stats.sdk.muxstats

import android.content.Context
import android.view.View
import androidx.annotation.VisibleForTesting
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.AdsLoader
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
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
import kotlin.math.ceil

/**
 * Mux Data SDK for ExoPlayer. Create an instance of this object with your [ExoPlayer] to monitor
 * and record its state. When you clean up your player, make sure to call [release] to ensure all
 * player-related resources are released
 *
 * If you are using Google IMA Ads, you must add our listener to your [AdsLoader] in order for the
 * Mux Data SDK to monitor ad-related events, using [getAdsImaSdkListener]:
 * AdsLoader.Builder(this)
 *    .setAdErrorListener(muxStats.getAdsImaSdkListener())
 *    .setAdEventListener(muxStats.getAdsImaSdkListener())
 *    //...
 *    build()
 *
 * Check out our full integration instructions for more information:
 * https://docs.mux.com/guides/data/monitor-exoplayer
 *
 * @param context The context you're playing in. Screen size will be detected if this is an Activity
 * @param player The player you wish to monitor
 * @param playerName A human-readable name for your player. It will be searchable on the dashboard
 * @param playerView The View the player is rendering on. For Audio-only, this can be omitted/null.
 * @param customerData Data about you, your video, and your player.
 * @param customOptions Options about the behavior of the SDK. Unless you have a special use case,
 *    this can be left null/omitted
 */
@Suppress("unused")
class MuxStatsExoPlayer @JvmOverloads constructor(
  val context: Context,
  val player: ExoPlayer,
  @Suppress("MemberVisibilityCanBePrivate") val playerView: View? = null,
  playerName: String,
  customerData: CustomerData,
  customOptions: CustomOptions? = null,
  network: INetworkRequest = MuxNetworkRequests()
) {
  // TODO: Add Extensions somewhere so in Kotlin you can go exoPlayer.monitorWithMuxData(view = player.getView())

  companion object {
    const val TAG = "MuxStatsExoPlayer"
  }

  private var _player by weak(player)
  private var _playerView by weak(playerView)

  private val eventBus = EventBus()
  private val collector = MuxStateCollector({ muxStats }, eventBus)
  private val playerAdapter = collector.createExoPlayerAdapter(
    context = context,
    playerView = playerView,
    player = player,
  )
  private val muxStats: MuxStats // Set in init{} because INetworkRequest must be set statically 1st

  /**
   * An [AdErrorEvent.AdErrorListener] and [AdEvent.AdEventListener] that can be used with an IMA
   * [AdsLoader] to monitor ad-related events with Mux Data
   */
  @Suppress("MemberVisibilityCanBePrivate")
  val imaSdkListener: AdsImaSDKListener? by lazy {
    AdsImaSDKListener.createIfImaAvailable(
      player,
      collector,
      eventBus
    )
  }

  /**
   * The view being used by the player that is being monitored, safely cast as a StyledPlayerView if
   * possible. Null if there is no view or the view is not the right type
   */
  // TODO need to be splitted by Exo variants
//  var styledPlayerView: StyledPlayerView? by downcast(::_playerView)

  init {
    // Init MuxStats (muxStats must be created last)
    MuxStats.setHostDevice(MuxDevice(context))
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
   * Gets a Listener for google IMA ads events. You can add this listener to your [AdsLoader] and
   * Mux Data will monitor ad-related events
   *
   * See https://docs.mux.com/guides/data/monitor-exoplayer#4-advanced for more information
   *
   * For example:
   *  AdsLoader.Builder(this)
   *    .setAdErrorListener(muxStats.getAdsImaSdkListener())
   *    .setAdEventListener(muxStats.getAdsImaSdkListener())
   *    //...
   *    build()
   *
   * @return the IMA SDK Listener
   */
  fun getAdsImaSdkListener(): AdsImaSDKListener? = imaSdkListener

  /**
   * Monitor an instance of Google IMA SDK's AdsLoader. This method should only be used with
   * ExoPlayer 2.11 and below
   *
   * See https://docs.mux.com/guides/data/monitor-exoplayer#4-advanced for more information
   *
   * @param adsLoader The AdsLoader to monitor. This method shouldn't be used on ExoPlayer 2.12 and
   *    up
   */
  @Deprecated(
    message = "This method should only be used with ExoPlayer versions 2.11 and below. If you are "
            + "still on that version, this warning can be suppressed",
    replaceWith = ReplaceWith("getAdsImaSdkListener")
  )
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

  /**
   * Gets the PlayerView casted as a View with the type of the receiving object. If the types don't
   * match at runtime, or if [playerView] is null, this will return null
   */
  inline fun <reified T : View> getExoPlayerView(): T? = playerView as? T

  /**
   * Overwrite the OS version reported on the Mux Data dashboard for views recorded with this object
   */
  fun overwriteOsVersion(osVersion: String) {
    singletonDevice()?.overwrittenOsVersion = osVersion
  }

  /**
   * Overwrite the device name reported on the Mux Data dashboard for views recorded with this object
   */
  fun overwriteDeviceName(deviceName: String) {
    singletonDevice()?.overwrittenDeviceName = deviceName
  }

  /**
   * Overwrite the OS Family name (Such as 'Android')  reported on the Mux Data dashboard for views
   * recorded with this object
   */
  fun overwriteOsFamily(osFamily: String) {
    singletonDevice()?.overwrittenOsFamilyName = osFamily
  }

  /**
   * Overwrite the OS Family name (Such as 'Android')  reported on the Mux Data dashboard for views
   * recorded with this object
   */
  fun overwriteManufacturer(manufacturer: String) {
    singletonDevice()?.overwrittenManufacturer = manufacturer
  }

  /**
   * If true, this object will automatically track fatal playback errors, eventually showing the
   * errors on the dashboard. If false, only errors reported via [error] will show up on the
   * dashboard
   */
  fun setAutomaticErrorTracking(enabled: Boolean) = muxStats.setAutomaticErrorTracking(enabled)

  /**
   * Report a fatal error to the dashboard for this view. This is normally tracked automatically,
   * but if you are reporting errors yourself, you can do so with this method
   */
  fun error(exception: MuxErrorException) = muxStats.error(exception)

  /**
   * Change the player [View] this object observes.
   * @see [getExoPlayerView]
   */
  fun setPlayerView(view: View?) {
    playerAdapter.playerView = view
  }

  /**
   * Manually set the size of the player view. This overrides the normal auto-detection. The
   * dimensions should be in physical pixels
   */
  fun setPlayerSize(widthPx: Int, heightPx: Int) = muxStats.setPlayerSize(pxToDp(widthPx), pxToDp(heightPx))

  /**
   * Manually set the size of the screen. This overrides the normal auto-detection. The dimensions
   * should be in physical pixels
   */
  fun setScreenSize(widthPx: Int, heightPx: Int) = muxStats.setScreenSize(pxToDp(widthPx), pxToDp(heightPx))

  /**
   * Call when a new [MediaItem] is being played in a player. This will start a new View to
   * represent the new video being consumed
   */
  fun videoChange(videoData: CustomerVideoData) {
    collector.videoChange(videoData)
  }

  /**
   * Call when new content is being served over the same URL, such as during a live stream. This
   * method will start a new view to represent the new content being consumed
   */
  fun programChange(videoData: CustomerVideoData) = collector.programChange(videoData)

  /**
   * Call when the device changes physical orientation, such as moving from portrait to landscape
   */
  fun orientationChange(orientation: MuxSDKViewOrientation) =
    muxStats.orientationChange(orientation)

  /**
   * Call when the presentation of the video changes, ie Fullscreen vs Normal, etc
   */
  fun presentationChange(presentation: MuxSDKViewPresentation) =
    muxStats.presentationChange(presentation)

  /**
   * Dispatch a raw event to the View. Please use this method with caution, as unexpected events can
   * lead to broken views
   */
  fun dispatch(event: IEvent?) = eventBus.dispatch(event)

  /**
   * Enables ADB logging for this SDK
   * @param enable If true, enables logging. If false, disables logging
   * @param verbose If true, enables verbose logging. If false, disables it
   */
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
   * Allow HTTP headers with a given name to be passed to the backend. By default we ignore all HTTP
   * headers that are not in the [MuxStateCollectorBase.allowedHeaders] list.
   * This is used in automated tests and is not intended to be used from the application layer.
   *
   * @param headerName name of the header to send to the backend.
   */
  @Suppress("ProtectedInFinal")
  @VisibleForTesting
  protected fun allowHeaderToBeSentToBackend(headerName: String?) {
    collector.allowHeaderToBeSentToBackend(headerName)
  }

  /**
   * Returns whether or not the state collector thinks the player is paused. This method is used in
   * automated tests and is not intended for use at the application layer
   */
  @Suppress("ProtectedInFinal")
  @VisibleForTesting
  protected fun isPaused() = playerAdapter.collector.isPaused()

  /**
   * Convert physical pixels to device density independent pixels.
   *
   * @param px physical pixels to be converted.
   * @return number of density pixels calculated.
   */
  private fun pxToDp(px: Int): Int {
    val displayMetrics = context.resources.displayMetrics
    return ceil((px / displayMetrics.density).toDouble()).toInt()
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
