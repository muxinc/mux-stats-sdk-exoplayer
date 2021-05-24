package com.mux.stats.sdk.muxstats;

import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.mux.stats.sdk.core.events.playback.AdBreakEndEvent;
import com.mux.stats.sdk.core.events.playback.AdBreakStartEvent;
import com.mux.stats.sdk.core.events.playback.AdEndedEvent;
import com.mux.stats.sdk.core.events.playback.AdFirstQuartileEvent;
import com.mux.stats.sdk.core.events.playback.AdMidpointEvent;
import com.mux.stats.sdk.core.events.playback.AdPauseEvent;
import com.mux.stats.sdk.core.events.playback.AdPlayEvent;
import com.mux.stats.sdk.core.events.playback.AdPlayingEvent;
import com.mux.stats.sdk.core.events.playback.AdThirdQuartileEvent;
import com.mux.stats.sdk.core.events.playback.PlaybackEvent;
import com.mux.stats.sdk.core.model.ViewData;

public class AdsImaSDKListener implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {

  private final MuxBaseExoPlayer exoPlayerListener;
  private boolean sendPlayOnStarted = false;
  private boolean missingAdBreakStartEvent = false;

  public AdsImaSDKListener(MuxBaseExoPlayer listener) {
    exoPlayerListener = listener;
  }

  @Override
  public void onAdError(AdErrorEvent adErrorEvent) {
    if (exoPlayerListener != null) {
      PlaybackEvent event = new com.mux.stats.sdk.core.events.playback.AdErrorEvent(null);
      setupAdViewData(event, null);
      exoPlayerListener.dispatch(event);
    }
  }

  private void setupAdViewData(PlaybackEvent event, Ad ad) {
    ViewData viewData = new ViewData();
    if (exoPlayerListener.getCurrentPosition() == 0) {
      if (ad != null) {
        viewData.setViewPrerollAdId(ad.getAdId());
        viewData.setViewPrerollCreativeId(ad.getCreativeId());
      }
    }
    event.setViewData(viewData);
  }

  @Override
  public void onAdEvent(AdEvent adEvent) {
    if (exoPlayerListener != null && exoPlayerListener.player != null
        && exoPlayerListener.player.get() != null) {
      PlaybackEvent event = null;
      Ad ad = adEvent.getAd();
      switch (adEvent.getType()) {
        // Cases sorted in calling sequence
        case LOADED:
          // There is nothing needed here, for now. It is unclear what this event
          // actually correlates to with regards to VAST _AND_ VMAP responses.
          break;
        case CONTENT_PAUSE_REQUESTED:
          // Send pause event if we are currently playing or preparing to play content
          if (exoPlayerListener.getState() == MuxBaseExoPlayer.PlayerState.PLAY ||
              exoPlayerListener.getState() == MuxBaseExoPlayer.PlayerState.PLAYING) {
            exoPlayerListener.pause();
          }
          sendPlayOnStarted = false;
          exoPlayerListener.setState(MuxBaseExoPlayer.PlayerState.PLAYING_ADS);
          if (!exoPlayerListener.player.get().getPlayWhenReady() &&
              (exoPlayerListener.player.get().getCurrentPosition() == 0)) {
            // This is preroll ads when play when ready is set to false, we need to ignore these events
            missingAdBreakStartEvent = true;
            break;
          }
          exoPlayerListener.setState(MuxBaseExoPlayer.PlayerState.PLAYING_ADS);
          dispatchAdPlaybackEvent(new AdBreakStartEvent(null), ad);
          dispatchAdPlaybackEvent(new AdPlayEvent(null), ad);
          break;
        case STARTED:
          // On the first STARTED, do not send AdPlay, as it was handled in
          // CONTENT_PAUSE_REQUESTED
          if (sendPlayOnStarted) {
            dispatchAdPlaybackEvent(new AdPlayEvent(null), ad);
          } else {
            sendPlayOnStarted = true;
          }
          dispatchAdPlaybackEvent(new AdPlayingEvent(null), ad);
          break;
        case FIRST_QUARTILE:
          dispatchAdPlaybackEvent(new AdFirstQuartileEvent(null), ad);
          break;
        case MIDPOINT:
          dispatchAdPlaybackEvent(new AdMidpointEvent(null), ad);
          break;
        case THIRD_QUARTILE:
          dispatchAdPlaybackEvent(new AdThirdQuartileEvent(null), ad);
          break;
        case COMPLETED:
          dispatchAdPlaybackEvent(new AdEndedEvent(null), ad);
          break;
        case CONTENT_RESUME_REQUESTED:
          // End the ad break, and then toggle playback state to ensure that
          // we get a play/playing after the ads.
          dispatchAdPlaybackEvent(new AdBreakEndEvent(null), ad);
          exoPlayerListener.player.get().setPlayWhenReady(false);
          exoPlayerListener.setState(MuxBaseExoPlayer.PlayerState.FINISHED_PLAYING_ADS);
          exoPlayerListener.player.get().setPlayWhenReady(true);
          break;
        case PAUSED:
          if (!exoPlayerListener.player.get().getPlayWhenReady() &&
              (exoPlayerListener.player.get().getCurrentPosition() == 0)) {
            // This is preroll ads when play when ready is set to false, we need to ignore these events
            break;
          }
          dispatchAdPlaybackEvent(new AdPauseEvent(null), ad);
          break;
        case RESUMED:
          if (missingAdBreakStartEvent) {
            // This is special case when we have ad preroll and play when ready is set to false
            // in that case we need to dispatch AdBreakStartEvent first and resume the playback.
            dispatchAdPlaybackEvent(new AdBreakStartEvent(null), ad);
            dispatchAdPlaybackEvent(new AdPlayEvent(null), ad);
            missingAdBreakStartEvent = false;
          } else {
            dispatchAdPlaybackEvent(new AdPlayEvent(null), ad);
            dispatchAdPlaybackEvent(new AdPlayingEvent(null), ad);
          }
          break;
        case ALL_ADS_COMPLETED:
          // Nothing to do here, as this depends on VAST vs VMAP and is not
          // consistent between the two.
          break;
        default:
          return;
      }
    }
  }

  private void dispatchAdPlaybackEvent(PlaybackEvent event, Ad ad) {
    setupAdViewData(event, ad);
    exoPlayerListener.dispatch(event);
  }
}
