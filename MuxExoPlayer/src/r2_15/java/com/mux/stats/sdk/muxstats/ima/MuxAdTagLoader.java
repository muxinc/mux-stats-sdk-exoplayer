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

import android.content.Context;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.mux.stats.sdk.muxstats.ima.MuxImaUtil.Configuration;
import com.mux.stats.sdk.muxstats.ima.MuxImaUtil.ImaFactory;
import java.util.List;

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
    TrackSelectionArray trackSelections = player.getCurrentTrackSelections();
    return TrackSelectionUtil.hasTrackOfType(trackSelections, C.TRACK_TYPE_AUDIO) ? 100 : 0;
  }

}
