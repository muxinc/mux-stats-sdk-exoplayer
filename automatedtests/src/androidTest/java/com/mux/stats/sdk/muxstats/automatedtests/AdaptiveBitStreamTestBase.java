package com.mux.stats.sdk.muxstats.automatedtests;

import static com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO;
import static org.junit.Assert.fail;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.mux.stats.sdk.core.events.playback.RenditionChangeEvent;
import com.mux.stats.sdk.core.model.VideoData;
import java.util.ArrayList;
import org.json.JSONException;
import org.json.JSONObject;

public class AdaptiveBitStreamTestBase extends TestBase {

  TrackGroupArray getVideoTrackGroupArray() {
    DefaultTrackSelector selector = testActivity.getTrackSelector();
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo = selector.getCurrentMappedTrackInfo();
    for (int rendererTrackIndex = 0; rendererTrackIndex < mappedTrackInfo.getRendererCount();
        rendererTrackIndex++) {
      int trackType = mappedTrackInfo.getRendererType(rendererTrackIndex);
      if (trackType == TRACK_TYPE_VIDEO) {
        return mappedTrackInfo
            .getTrackGroups(rendererTrackIndex);
      }

    }
    return null;
  }

  ArrayList<Format> getAvailableVideoRendition() {
    ArrayList<Format> result = new ArrayList<>();
    TrackGroupArray videoTrackGroupArray = getVideoTrackGroupArray();
    for (int trackIndex = 0; trackIndex < videoTrackGroupArray.length; trackIndex++) {
      TrackGroup trackGroup = videoTrackGroupArray.get(trackIndex);
      for (int formatIndex = 0; formatIndex < trackGroup.length; formatIndex++) {
        result.add(trackGroup.getFormat(formatIndex));
      }
    }
    return result;
  }

  int getSelectedRenditionIndex() throws JSONException {
    int renditionChangeIndex = networkRequest.getIndexForFirstEvent(RenditionChangeEvent.TYPE);
    if (renditionChangeIndex == -1) {
      return -1;
    }
    JSONObject jo = networkRequest.getEventForIndex(renditionChangeIndex);
    if (jo == null || !jo.has(VideoData.VIDEO_SOURCE_WIDTH) || !jo
        .has(VideoData.VIDEO_SOURCE_HEIGHT)) {
      return -1;
    }
    int videoWidth = jo.getInt(VideoData.VIDEO_SOURCE_WIDTH);
    int videoHeight = jo.getInt(VideoData.VIDEO_SOURCE_HEIGHT);

    ArrayList<Format> availableFormats = getAvailableVideoRendition();
    int index = 0;
    for (Format fmt : availableFormats) {
      if (Math.abs(fmt.width - videoWidth) < 10 &&
          Math.abs(fmt.height - videoHeight) < 10) {
        return index;
      }
      index++;
    }
    String availableFormatsStr = "";
    for (Format fmt : availableFormats) {
      availableFormatsStr += "(" + fmt.width + "x" + fmt.height + ") ";
    }
    fail("Reported source resolution: " + videoWidth + "x" + videoHeight +
        " do not match any of available formats: " + availableFormatsStr);
    return -1;
  }

  void switchRenditionToIndex(int index) {
    Format fmt = getAvailableVideoRendition().get(index);
    DefaultTrackSelector selector = testActivity.getTrackSelector();
    selector.setParameters(selector.buildUponParameters()
        .setMaxVideoSize(fmt.width, fmt.height)
        .setForceHighestSupportedBitrate(true)
    );
  }
}
