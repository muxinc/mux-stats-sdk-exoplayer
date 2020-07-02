package com.mux.stats.sdk.muxstats;

import android.util.Log;

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
import com.mux.stats.sdk.core.events.playback.AdRequestEvent;
import com.mux.stats.sdk.core.events.playback.AdResponseEvent;
import com.mux.stats.sdk.core.events.playback.AdThirdQuartileEvent;
import com.mux.stats.sdk.core.events.playback.PlaybackEvent;
import com.mux.stats.sdk.core.model.ViewData;

import static com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType.CONTENT_RESUME_REQUESTED;

public class AdsImaSDKListener implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {
    private MuxBaseExoPlayer exoPlayerListener;
    private boolean needSendAdResponse = false;
    private boolean inAdBreak = false;
    private int adCount = 0;

    public AdsImaSDKListener() {

    }

    public AdsImaSDKListener(MuxBaseExoPlayer listener) {
        exoPlayerListener = listener;
    }

    public void setExoPlayerListener(MuxBaseExoPlayer listener) {
        exoPlayerListener = listener;
    }

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        inAdBreak = false;
        adCount = 0;
        if (exoPlayerListener != null) {
            PlaybackEvent event = new com.mux.stats.sdk.core.events.playback.AdErrorEvent(null);
            setupAdViewData(event, null);
            exoPlayerListener.dispatch(event);
        }
    }

    private void setupAdViewData(PlaybackEvent event, Ad ad) {
        if (event == null) {
            return;
        }
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
        if (exoPlayerListener != null) {
            PlaybackEvent event = null;
            Ad ad = adEvent.getAd();
            switch (adEvent.getType()) {
                case LOADED:
                    if (adCount == 0) {
                        if (exoPlayerListener.getState() == MuxBaseExoPlayer.PlayerState.PLAY
                                || exoPlayerListener.getState() == MuxBaseExoPlayer.PlayerState.PLAYING) {
                            exoPlayerListener.pause();
                        }
                        inAdBreak = true;
                        event = new AdBreakStartEvent(null);
                        setupAdViewData(event, ad);
                        exoPlayerListener.dispatch(event);
                        event = new AdRequestEvent(null);
                        needSendAdResponse = true;
                    } else {
                        event = new AdPlayEvent(null);
                    }
                    adCount++;
                    break;
                case CONTENT_PAUSE_REQUESTED:
                    if (needSendAdResponse) {
                        event = new AdResponseEvent(null);
                        setupAdViewData(event, ad);
                        exoPlayerListener.dispatch(event);
                        needSendAdResponse = false;
                    }
                    event = new AdPlayEvent(null);
                    break;
                case STARTED:
                    event = new AdPlayingEvent(null);
                    break;
                case FIRST_QUARTILE:
                    event = new AdFirstQuartileEvent(null);
                    break;
                case MIDPOINT:
                    event = new AdMidpointEvent(null);
                    break;
                case THIRD_QUARTILE:
                    event = new AdThirdQuartileEvent(null);
                    break;
                case COMPLETED:
                    event = new AdEndedEvent(null);
                    break;
                case CONTENT_RESUME_REQUESTED:
                    adCount = 0;
                    event = new AdBreakEndEvent(null);
                    inAdBreak = false;
                    break;
                case PAUSED:
                    // called on event
                    event = new AdPauseEvent(null);
                    break;
                case RESUMED:
                    // called on event
                    event = new AdPlayEvent(null);
                    setupAdViewData(event, ad);
                    exoPlayerListener.dispatch(event);
                    event = new AdPlayingEvent(null);
                    break;
                default:
                    return;
            }
            if (event != null) {
                setupAdViewData(event, ad);
                exoPlayerListener.dispatch(event);
            }
            // Check if play and playing event for content have been fired before we received this event
            if(adEvent.getType() == CONTENT_RESUME_REQUESTED) {
                if (exoPlayerListener.isMissedAfterAdsPlayEvent()) {
                    exoPlayerListener.play();
                }
                if (exoPlayerListener.isMissedAfterAdsPlayingEvent()) {
                    exoPlayerListener.playing();
                }
            }
        }
    }

    public boolean getInAdBreak() {
        return inAdBreak;
    }

    public boolean getNeedToSendAdResponse() {
        return needSendAdResponse;
    }
}
