/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package  com.mux.stats.sdk.muxstats.ima;

import static com.google.android.exoplayer2.Player.COMMAND_GET_VOLUME;
import static com.mux.stats.sdk.muxstats.ima.MuxImaUtil.BITRATE_UNSET;
import static com.mux.stats.sdk.muxstats.ima.MuxImaUtil.TIMEOUT_UNSET;
import static com.mux.stats.sdk.muxstats.ima.MuxImaUtil.getAdGroupTimesUsForCuePoints;
import static com.mux.stats.sdk.muxstats.ima.MuxImaUtil.getImaLooper;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.max;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.view.ViewGroup;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdError;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener;
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.AdMediaInfo;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.AdsLoader.EventListener;
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException;
import com.google.android.exoplayer2.ui.AdOverlayInfo;
import com.google.android.exoplayer2.ui.AdViewProvider;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mux.stats.sdk.muxstats.ima.MuxImaUtil.Configuration;
import com.mux.stats.sdk.muxstats.ima.MuxImaUtil.ImaFactory;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Handles loading and playback of a single ad tag. */
final class MuxAdTagLoader extends MuxAdTagLoaderBase {

  /**
   * Creates a new ad tag loader, starting the ad request if the ad tag is valid.
   *
   * @param context
   * @param configuration
   * @param imaFactory
   * @param supportedMimeTypes
   * @param adTagDataSpec
   * @param adsId
   * @param adViewGroup
   */
  public MuxAdTagLoader(Context context,
      Configuration configuration,
      ImaFactory imaFactory, List<String> supportedMimeTypes,
      DataSpec adTagDataSpec, Object adsId, @Nullable ViewGroup adViewGroup) {
    super(context, configuration, imaFactory, supportedMimeTypes, adTagDataSpec, adsId,
        adViewGroup);
  }

  @Override
  protected int getPlayerVolumePercent() {
    @Nullable Player player = this.player;
    if (player == null) {
      return lastVolumePercent;
    }

    if (player.isCommandAvailable(COMMAND_GET_VOLUME)) {
      return (int) (player.getVolume() * 100);
    }

    // Check for a selected track using an audio renderer.
    return player.getCurrentTracksInfo().isTypeSelected(C.TRACK_TYPE_AUDIO) ? 100 : 0;
  }

}
