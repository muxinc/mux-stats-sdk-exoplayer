package com.mux.stats.sdk.muxstats.automatedtests;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.RenditionChangeEvent;
import com.mux.stats.sdk.core.model.VideoData;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO;
import static org.junit.Assert.fail;

public class RenditionChangeTests extends TestBase {


    @Before
    public void init(){
        urlToPlay = "http://localhost:5000/hls/google_glass/playlist.m3u8";
        // These video have larger bitrate, make sure we do not cause any
        // rebuffering due to low bandwith
        bandwidthLimitInBitsPerSecond = 12000000;
        super.init();
    }

    @Test
    public void testRenditionChange() {
        try {
            if(!testActivity.waitForPlaybackToStart(waitForPlaybackToStartInMS)) {
                fail("Playback did not start in " + waitForPlaybackToStartInMS + " milliseconds !!!");
            }
            Thread.sleep(PLAY_PERIOD_IN_MS);
            // Switch rendition
            int startingFmtIndex = getSelectedRenditionIndex();
            ArrayList<Format> availableFormats = getAvailableVideoRendition();
            Format startingFmt = availableFormats.get(startingFmtIndex);
            int nextFmtIndex;
            if (startingFmtIndex < availableFormats.size() -2) {
                nextFmtIndex = startingFmtIndex + 1;
            } else {
                nextFmtIndex = startingFmtIndex -1;
            }
            Format changedFmt = availableFormats.get(nextFmtIndex);
            switchRenditionToIndex(nextFmtIndex);
            Thread.sleep(PLAY_PERIOD_IN_MS);
            int renditionChangeIndex = networkRequest
                    .getIndexForLastEvent(RenditionChangeEvent.TYPE);
            int playinIndex = networkRequest.getIndexForFirstEvent(PlayingEvent.TYPE);
            JSONObject jo = networkRequest.getEventForIndex(renditionChangeIndex);
            int videoWidth = jo.getInt(VideoData.VIDEO_SOURCE_WIDTH);
            int videoHeight = jo.getInt(VideoData.VIDEO_SOURCE_HEIGHT);
            if (videoWidth != changedFmt.width && videoHeight != changedFmt.height) {
                fail("Last reported rendition change width and height (" + videoWidth + "x" +
                        videoHeight + ") do not match requested format resolution: (" +
                        changedFmt.width + "x" + changedFmt.height + ")");
            }
            long lastRenditionChangeAt = networkRequest
                    .getCreationTimeForEvent(renditionChangeIndex) - networkRequest
                    .getCreationTimeForEvent(playinIndex);
            long lastRenditionChangeIndexExpectedAt = PLAY_PERIOD_IN_MS ;
            if (Math.abs(lastRenditionChangeAt - lastRenditionChangeIndexExpectedAt) > 500) {
                fail("Last rendition change event reported at: " + lastRenditionChangeAt +
                        ", expected time: " + lastRenditionChangeIndexExpectedAt +
                        ", renditionChangeEventIndex: " + renditionChangeIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    TrackGroupArray getVideoTrackGroupArray() {
        DefaultTrackSelector selector = testActivity.getTrackSelector();
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = selector.getCurrentMappedTrackInfo();
        for(int rendererTrackIndex = 0; rendererTrackIndex < mappedTrackInfo.getRendererCount();
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
            for (int formatIndex = 0; formatIndex < trackGroup.length; formatIndex ++) {
                result.add(trackGroup.getFormat(formatIndex));
            }
        }
        return result;
    }

    int getSelectedRenditionIndex() throws JSONException {
        int renditionChangeEventIndex = networkRequest
                .getIndexForFirstEvent(RenditionChangeEvent.TYPE);
        JSONObject jo = networkRequest.getEventForIndex(renditionChangeEventIndex);
        int videoWidth = jo.getInt(VideoData.VIDEO_SOURCE_WIDTH);
        int videoHeight = jo.getInt(VideoData.VIDEO_SOURCE_HEIGHT);
        ArrayList<Format> availableFormats = getAvailableVideoRendition();
        int index = 0;
        for (Format fmt:availableFormats) {
            if (Math.abs(fmt.width - videoWidth) > 10 &&
                    Math.abs(fmt.height - videoHeight) > 10) {
                return index;
            }
            index ++;
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
        selector.setParameters(selector.buildUponParameters().setMaxVideoSize(fmt.width, fmt.height));
    }
}
