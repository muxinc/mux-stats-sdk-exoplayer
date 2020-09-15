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
import com.mux.stats.sdk.core.events.playback.AdRequestEvent;
import com.mux.stats.sdk.core.events.playback.AdResponseEvent;
import com.mux.stats.sdk.core.events.playback.AdThirdQuartileEvent;
import com.mux.stats.sdk.core.events.playback.PlaybackEvent;
import com.mux.stats.sdk.core.model.ViewData;

public class AdsImaSDKListener implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {
    private MuxBaseExoPlayer exoPlayerListener;
    private boolean needSendAdResponse = false;

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
        if (exoPlayerListener != null) {
            Ad ad = adEvent.getAd();
            switch (adEvent.getType()) {
                case CONTENT_PAUSE_REQUESTED:
                    if (needSendAdResponse) {
                        dispatchAdPlaybackEvent(new AdResponseEvent(null), ad);
                        needSendAdResponse = false;
                    }
                    dispatchAdPlaybackEvent(new AdBreakStartEvent(null), ad);
                    return;
                case CONTENT_RESUME_REQUESTED:
                    dispatchAdPlaybackEvent(new AdBreakEndEvent(null), ad);
                    return;
                case LOADED:
                    dispatchAdPlaybackEvent(new AdRequestEvent(null), ad);
                    needSendAdResponse = true;
                    return;
                case STARTED:
                    dispatchAdPlaybackEvent(new AdPlayEvent(null), ad);
                    dispatchAdPlaybackEvent(new AdPlayingEvent(null), ad);
                    return;
                case FIRST_QUARTILE:
                    dispatchAdPlaybackEvent(new AdFirstQuartileEvent(null), ad);
                    return;
                case MIDPOINT:
                    dispatchAdPlaybackEvent(new AdMidpointEvent(null), ad);
                    return;
                case THIRD_QUARTILE:
                    dispatchAdPlaybackEvent(new AdThirdQuartileEvent(null), ad);
                    return;
                case COMPLETED:
                    dispatchAdPlaybackEvent(new AdEndedEvent(null), ad);
                    return;
                case PAUSED:
                    dispatchAdPlaybackEvent(new AdPauseEvent(null), ad);
                    return;
                case RESUMED:
                    dispatchAdPlaybackEvent(new AdPlayEvent(null), ad);
                    dispatchAdPlaybackEvent(new AdPlayingEvent(null), ad);
                    return;
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
