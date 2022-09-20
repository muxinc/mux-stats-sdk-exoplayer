package com.mux.stats.sdk.muxstats.ima;


import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MuxImaAdsLoader extends MuxImaAdsLoaderBase {

  private MuxImaAdsLoader(
      Context context,
      MuxImaUtil.Configuration configuration, MuxImaUtil.ImaFactory imaFactory) {
    super(context, configuration, imaFactory);
  }

  public static class Builder extends BuilderBase {
    public Builder(Context context) {
      super(context);
    }

    public MuxImaAdsLoader build() {
      return new MuxImaAdsLoader(
          context,
          new MuxImaUtil.Configuration(
              adPreloadTimeoutMs,
              vastLoadTimeoutMs,
              mediaLoadTimeoutMs,
              focusSkipButtonWhenAvailable,
              playAdBeforeStartPosition,
              mediaBitrate,
              enableContinuousPlayback,
              adMediaMimeTypes,
              adUiElements,
              companionAdSlots,
              adErrorListeners,
              adEventListeners,
              videoAdPlayerCallback,
              imaSdkSettings,
              debugModeEnabled),
          imaFactory);
    }
  }

  @Override
  public void setSupportedContentTypes(@C.ContentType int... contentTypes) {
    List<String> supportedMimeTypes = new ArrayList<>();
    for (@C.ContentType int contentType : contentTypes) {
      // IMA does not support Smooth Streaming ad media.
      if (contentType == C.TYPE_DASH) {
        supportedMimeTypes.add(MimeTypes.APPLICATION_MPD);
      } else if (contentType == C.TYPE_HLS) {
        supportedMimeTypes.add(MimeTypes.APPLICATION_M3U8);
      } else if (contentType == C.TYPE_OTHER) {
        supportedMimeTypes.addAll(
            Arrays.asList(
                MimeTypes.VIDEO_MP4,
                MimeTypes.VIDEO_WEBM,
                MimeTypes.VIDEO_H263,
                MimeTypes.AUDIO_MP4,
                MimeTypes.AUDIO_MPEG));
      }
    }
    this.supportedMimeTypes = Collections.unmodifiableList(supportedMimeTypes);
  }

  @Override
  protected void maybePreloadNextPeriodAds() {
    @Nullable Player player = this.player;
    if (player == null) {
      return;
    }
    Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) {
      return;
    }
    int nextPeriodIndex =
        timeline.getNextPeriodIndex(
            player.getCurrentPeriodIndex(),
            period,
            window,
            player.getRepeatMode(),
            player.getShuffleModeEnabled());
    if (nextPeriodIndex == C.INDEX_UNSET) {
      return;
    }
    timeline.getPeriod(nextPeriodIndex, period);
    @Nullable Object nextAdsId = period.getAdsId();
    if (nextAdsId == null) {
      return;
    }
    @Nullable MuxAdTagLoaderBase nextAdTagLoader = adTagLoaderByAdsId.get(nextAdsId);
    if (nextAdTagLoader == null || nextAdTagLoader == currentMuxAdTagLoader) {
      return;
    }
    long periodPositionUs =
        timeline.getPeriodPosition(
            window, period, period.windowIndex, /* windowPositionUs= */ C.TIME_UNSET)
            .second;
    nextAdTagLoader.maybePreloadAds(C.usToMs(periodPositionUs), C.usToMs(period.durationUs));
  }
}
