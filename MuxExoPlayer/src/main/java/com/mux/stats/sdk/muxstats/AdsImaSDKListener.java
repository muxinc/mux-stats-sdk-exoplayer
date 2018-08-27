package com.mux.stats.sdk.muxstats;

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

public class AdsImaSDKListener implements AdErrorEvent.AdErrorListener, AdEvent.AdEventListener {
    private MuxBaseExoPlayer exoPlayerListener;

    public AdsImaSDKListener(MuxBaseExoPlayer listener) {
        exoPlayerListener = listener;
    }

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        if (exoPlayerListener != null)
            exoPlayerListener.dispatch(new com.mux.stats.sdk.core.events.playback.AdErrorEvent(null));
    }

    @Override
    public void onAdEvent(AdEvent adEvent) {
        if (exoPlayerListener != null) {
            switch (adEvent.getType()) {
                case LOADED:
                    exoPlayerListener.dispatch(new AdBreakStartEvent(null));
                    break;
                case CONTENT_PAUSE_REQUESTED:
                    exoPlayerListener.dispatch(new AdPlayEvent(null));
                    break;
                case STARTED:
                    exoPlayerListener.dispatch(new AdPlayingEvent(null));
                    break;
                case FIRST_QUARTILE:
                    exoPlayerListener.dispatch(new AdFirstQuartileEvent(null));
                    break;
                case MIDPOINT:
                    exoPlayerListener.dispatch(new AdMidpointEvent(null));
                    break;
                case THIRD_QUARTILE:
                    exoPlayerListener.dispatch(new AdThirdQuartileEvent(null));
                    break;
                case COMPLETED:
                    exoPlayerListener.dispatch(new AdEndedEvent(null));
                    break;
                case CONTENT_RESUME_REQUESTED:
                    exoPlayerListener.dispatch(new AdBreakEndEvent(null));
                    break;
                case PAUSED:
                    exoPlayerListener.dispatch(new AdPauseEvent(null));
                    break;
                case RESUMED:
                    exoPlayerListener.dispatch(new AdPlayingEvent(null));
                    break;
                default:
                    break;
            }
        }
    }

    public void onAdRequested() {
        exoPlayerListener.dispatch(new AdRequestEvent(null));
    }

    public void onAdResponsed() {
        exoPlayerListener.dispatch(new AdResponseEvent(null));
    }
}
