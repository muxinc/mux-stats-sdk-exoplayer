package com.mux.stats.sdk.muxstats

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.annotation.VisibleForTesting
import com.google.ads.interactivemedia.v3.api.AdsLoader
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
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
import java.lang.ref.WeakReference
import java.util.*
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

  private val imaSdkListener: AdsImaSDKListener? by lazy {
    AdsImaSDKListener.createIfImaAvailable(
      player,
      collector,
      eventBus
    )
  }

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
  @Suppress("MemberVisibilityCanBePrivate")
  inline fun <reified T : View> getExoPlayerView(): T? = playerView as? T

  /**
   * Overwrite the OS version reported on the Mux Data dashboard for views recorded with this object
   */
  fun overwriteOsVersion(osVersion: String) {
    MuxDevice.muxStatsInstance?.overwrittenOsVersion = osVersion
  }

  /**
   * Overwrite the device name reported on the Mux Data dashboard for views recorded with this object
   */
  fun overwriteDeviceName(deviceName: String) {
    MuxDevice.muxStatsInstance?.overwrittenDeviceName = deviceName
  }

  /**
   * Overwrite the OS Family name (Such as 'Android')  reported on the Mux Data dashboard for views
   * recorded with this object
   */
  fun overwriteOsFamily(osFamily: String) {
    MuxDevice.muxStatsInstance?.overwrittenOsFamilyName = osFamily
  }

  /**
   * Overwrite the OS Family name (Such as 'Android')  reported on the Mux Data dashboard for views
   * recorded with this object
   */
  fun overwriteManufacturer(manufacturer: String) {
    MuxDevice.muxStatsInstance?.overwrittenManufacturer = manufacturer
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
  @Suppress("KDocUnresolvedReference")
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

  @Suppress("RedundantNullableReturnType") // Lots of java interaction here
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


/**
 * Basic device details such as OS version, vendor name and etc. Instances of this class
 * are used by [MuxStats] to interface with the device.
 */
private class MuxDevice(ctx: Context) : IDevice {

  private var contextRef: WeakReference<Context>
  private var deviceId: String?
  private var appName = ""
  private var appVersion = ""

  var overwrittenDeviceName: String? = null
  var overwrittenOsFamilyName: String? = null
  var overwrittenOsVersion: String? = null
  var overwrittenManufacturer: String? = null

  override fun getHardwareArchitecture(): String {
    return Build.HARDWARE
  }

  override fun getOSFamily(): String {
    return "Android"
  }

  override fun getMuxOSFamily(): String? = overwrittenOsFamilyName

  override fun getOSVersion(): String {
    return Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")"
  }

  override fun getMuxOSVersion(): String? = overwrittenOsVersion

  override fun getManufacturer(): String {
    return Build.MANUFACTURER
  }

  override fun getMuxManufacturer(): String? = overwrittenManufacturer

  override fun getModelName(): String {
    return Build.MODEL
  }

  override fun getMuxModelName(): String? = overwrittenDeviceName

  override fun getPlayerVersion(): String {
    return ExoPlayerLibraryInfo.VERSION
  }

  override fun getDeviceId(): String {
    return deviceId ?: "unknown"
  }

  override fun getAppName(): String {
    return appName
  }

  override fun getAppVersion(): String {
    return appVersion
  }

  override fun getPluginName(): String {
    return BuildConfig.MUX_PLUGIN_NAME
  }

  override fun getPluginVersion(): String {
    return BuildConfig.LIB_VERSION
  }

  override fun getPlayerSoftware(): String {
    return EXO_SOFTWARE
  }

  /**
   * Determine the correct network connection type.
   *
   * @return the connection type name.
   */
  override fun getNetworkConnectionType(): String? {
    // Checking internet connectivity
    val context = contextRef.get() ?: return null
    val connectivityMgr = context
      .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    var activeNetwork: NetworkInfo? = null
    if (connectivityMgr != null) {
      activeNetwork = connectivityMgr.activeNetworkInfo
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val nc = connectivityMgr
          .getNetworkCapabilities(connectivityMgr.activeNetwork)
        if (nc == null) {
          MuxLogger.d(
            TAG,
            "ERROR: Failed to obtain NetworkCapabilities manager !!!"
          )
          return null
        }
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
          CONNECTION_TYPE_WIRED
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
          CONNECTION_TYPE_WIFI
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
          CONNECTION_TYPE_CELLULAR
        } else {
          CONNECTION_TYPE_OTHER
        }
      } else {
        if (activeNetwork!!.type == ConnectivityManager.TYPE_ETHERNET) {
          CONNECTION_TYPE_WIRED
        } else if (activeNetwork.type == ConnectivityManager.TYPE_WIFI) {
          CONNECTION_TYPE_WIFI
        } else if (activeNetwork.type == ConnectivityManager.TYPE_MOBILE) {
          CONNECTION_TYPE_CELLULAR
        } else {
          CONNECTION_TYPE_OTHER
        }
      }
    }
    return null
  }

  override fun getElapsedRealtime(): Long {
    return SystemClock.elapsedRealtime()
  }

  override fun outputLog(logPriority: LogPriority, tag: String, msg: String) {
    when (logPriority) {
      LogPriority.ERROR -> Log.e(tag, msg)
      LogPriority.WARN -> Log.w(tag, msg)
      LogPriority.INFO -> Log.i(tag, msg)
      LogPriority.DEBUG -> Log.d(tag, msg)
      LogPriority.VERBOSE -> Log.v(tag, msg)
      else -> Log.v(tag, msg)
    }
  }

  /**
   * Print underlying [MuxStats] SDK messages on the logcat. This will only be
   * called if [com.mux.stats.sdk.muxstats.MuxStatsExoPlayer.enableMuxCoreDebug] is called with first argument as true
   *
   * @param tag tag to be used.
   * @param msg message to be printed.
   */
  override fun outputLog(tag: String, msg: String) {
    Log.v(tag, msg)
  }

  companion object {
    private const val TAG = "MuxDevice"
    private const val EXO_SOFTWARE = "ExoPlayer"
    const val CONNECTION_TYPE_CELLULAR = "cellular"
    const val CONNECTION_TYPE_WIFI = "wifi"
    const val CONNECTION_TYPE_WIRED = "wired"
    const val CONNECTION_TYPE_OTHER = "other"
    const val MUX_DEVICE_ID = "MUX_DEVICE_ID"

    val muxStatsInstance = MuxStats.getHostDevice() as? MuxDevice
  }

  /**
   * Basic constructor.
   *
   * @param ctx activity context, we use this to access different system services, like
   * [ConnectivityManager], or [PackageInfo].
   */
  init {
    val sharedPreferences = ctx
      .getSharedPreferences(MUX_DEVICE_ID, Context.MODE_PRIVATE)
    deviceId = sharedPreferences.getString(MUX_DEVICE_ID, null)
    if (deviceId == null) {
      deviceId = UUID.randomUUID().toString()
      val editor = sharedPreferences.edit()
      editor.putString(MUX_DEVICE_ID, deviceId)
      editor.commit()
    }
    contextRef = WeakReference(ctx)
    try {
      val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
      appName = pi.packageName
      appVersion = pi.versionName
    } catch (e: PackageManager.NameNotFoundException) {
      MuxLogger.d(TAG, "could not get package info")
    }
  }
}