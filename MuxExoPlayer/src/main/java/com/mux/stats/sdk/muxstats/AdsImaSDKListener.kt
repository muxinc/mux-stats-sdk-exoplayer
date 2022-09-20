package com.mux.stats.sdk.muxstats

import com.google.ads.interactivemedia.v3.api.Ad
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.android.exoplayer2.ExoPlayer
import com.mux.stats.sdk.core.events.EventBus
import com.mux.stats.sdk.core.events.playback.*
import com.mux.stats.sdk.core.model.ViewData
import com.mux.stats.sdk.muxstats.internal.oneOf
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Ima SDK wrapper, listen to ad events and dispatch the appropriate AdPlayback events accordingly.
 */
class AdsImaSDKListener private constructor(
  exoPlayer: ExoPlayer,
  private val stateCollector: MuxStateCollector,
  private val eventBus: EventBus
) : AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

  companion object {
    @JvmSynthetic
    internal fun createIfImaAvailable(
      exoPlayer: ExoPlayer,
      collector: MuxStateCollector,
      eventBus: EventBus
    ): AdsImaSDKListener? {
      return try {
        // Check for some classes that are definitely part of IMA
        Class.forName("com.google.ads.interactivemedia.v3.api.AdsLoader")
        Class.forName("com.google.ads.interactivemedia.v3.api.AdsManager")
        Class.forName("com.google.ads.interactivemedia.v3.api.AdErrorEvent")
        Class.forName("com.google.ads.interactivemedia.v3.api.AdEvent")
        Class.forName("com.google.ads.interactivemedia.v3.api.Ad")

        AdsImaSDKListener(exoPlayer, collector, eventBus)
      } catch (e: ClassNotFoundException) {
        null
      }
    }
  }

  /** The ExoPlayer that is playing the ads */
  private val exoPlayer by weak(exoPlayer)

  /** This value is used to detect if the user pressed the pause button when an ad was playing  */
  private var sendPlayOnStarted = false

  /**
   * This value is used in the special case of pre roll ads playing. This value will be set to
   * true when a pre roll is detected, and will be reverted back to false after dispatching the
   * AdBreakStart event.
   */
  private var missingAdBreakStartEvent = false

  /**
   * Handles the Ad error.
   *
   * @param adErrorEvent, Error to be handled.
   */
  override fun onAdError(adErrorEvent: AdErrorEvent) {
    val event: PlaybackEvent = AdErrorEvent(null)
    setupAdViewData(event, null)
    eventBus.dispatch(event)
  }

  /**
   * Update the adId and creativeAdId on given playback event.
   *
   * @param event, event to be updated.
   * @param ad, current ad event that is being processed.
   */
  private fun setupAdViewData(event: PlaybackEvent, ad: Ad?) {
    val viewData = ViewData()
    if (stateCollector.playbackPositionMills == 0L) {
      if (ad != null) {
        viewData.viewPrerollAdId = ad.adId
        viewData.viewPrerollCreativeId = ad.creativeId
      }
    }
    event.viewData = viewData
  }

  /**
   * This is the main boilerplate, all processing logic is contained here. Depending on the phase
   * the ad is in (a single ad can have multiple phases from loading to the ending) each of them
   * is handled here and an appropriate AdPlayback event is dispatched to the backend.
   *
   * @param adEvent
   */
  override fun onAdEvent(adEvent: AdEvent) {
    exoPlayer?.let { player ->
      val event: PlaybackEvent? = null
      val ad = adEvent.ad
      when (adEvent.type) {
        AdEvent.AdEventType.LOADED -> {}
        AdEvent.AdEventType.CONTENT_PAUSE_REQUESTED -> {
          // Send pause event if we are currently playing or preparing to play content
          if (stateCollector.muxPlayerState.oneOf(MuxPlayerState.PLAY, MuxPlayerState.PLAYING)) {
            stateCollector.pause()
          }
          sendPlayOnStarted = false
          stateCollector.playingAds()
          if (!player.playWhenReady && player.currentPosition == 0L) {
            // This is preroll ads when play when ready is set to false, we need to ignore these events
            missingAdBreakStartEvent = true
            return
          }
          dispatchAdPlaybackEvent(AdBreakStartEvent(null), ad)
          dispatchAdPlaybackEvent(AdPlayEvent(null), ad)
        }
        AdEvent.AdEventType.STARTED -> {
          // On the first STARTED, do not send AdPlay, as it was handled in
          // CONTENT_PAUSE_REQUESTED
          if (sendPlayOnStarted) {
            dispatchAdPlaybackEvent(AdPlayEvent(null), ad)
          } else {
            sendPlayOnStarted = true
          }
          dispatchAdPlaybackEvent(AdPlayingEvent(null), ad)
        }
        AdEvent.AdEventType.FIRST_QUARTILE -> dispatchAdPlaybackEvent(
          AdFirstQuartileEvent(null),
          ad
        )
        AdEvent.AdEventType.MIDPOINT -> dispatchAdPlaybackEvent(AdMidpointEvent(null), ad)
        AdEvent.AdEventType.THIRD_QUARTILE -> dispatchAdPlaybackEvent(
          AdThirdQuartileEvent(null),
          ad
        )
        AdEvent.AdEventType.COMPLETED -> dispatchAdPlaybackEvent(AdEndedEvent(null), ad)
        AdEvent.AdEventType.CONTENT_RESUME_REQUESTED -> {
          // End the ad break, and then toggle playback state to ensure that
          // we get a play/playing after the ads.
          dispatchAdPlaybackEvent(AdBreakEndEvent(null), ad)
          player.playWhenReady = false
          stateCollector.finishedPlayingAds()
          player.playWhenReady = true
        }
        AdEvent.AdEventType.PAUSED -> {
          if (!player.playWhenReady
            && player.currentPosition == 0L) {
            // This is preroll ads when play when ready is set to false, we need to ignore these events
            return;
          }
          dispatchAdPlaybackEvent(AdPauseEvent(null), ad)
        }
        AdEvent.AdEventType.RESUMED ->
          if (missingAdBreakStartEvent) {
            // This is special case when we have ad preroll and play when ready is set to false
            // in that case we need to dispatch AdBreakStartEvent first and resume the playback.
            dispatchAdPlaybackEvent(AdBreakStartEvent(null), ad)
            dispatchAdPlaybackEvent(AdPlayEvent(null), ad)
            missingAdBreakStartEvent = false
          } else {
            dispatchAdPlaybackEvent(AdPlayEvent(null), ad)
            dispatchAdPlaybackEvent(AdPlayingEvent(null), ad)
          }
        AdEvent.AdEventType.ALL_ADS_COMPLETED -> {}
        else -> return
      }
    }
  }

  /**
   * Prepare and dispatch the given playback event to the backend using @link exoPlayerListener.
   *
   * @param event, to be dispatched.
   * @param ad, ad being processed.
   */
  private fun dispatchAdPlaybackEvent(event: PlaybackEvent, ad: Ad?) {
    setupAdViewData(event, ad)
    eventBus.dispatch(event)
  }
}