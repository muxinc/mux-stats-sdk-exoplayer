package com.mux.stats.sdk.muxstats.automatedtests;

import android.util.Log;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.RenditionChangeEvent;
import com.mux.stats.sdk.core.model.VideoData;
import com.mux.stats.sdk.muxstats.MuxStatsExoPlayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO;
import static org.junit.Assert.fail;

public class RenditionChangeTests extends TestBase {

    static final String TAG = "RenditionChangeTests";

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
            if (startingFmtIndex == availableFormats.size() - 1) {
                nextFmtIndex = startingFmtIndex - 1;
            } else {
                nextFmtIndex = startingFmtIndex + 1;
            }
            Format changedFmt = availableFormats.get(nextFmtIndex);
            switchRenditionToIndex(nextFmtIndex);
            Thread.sleep(PLAY_PERIOD_IN_MS);
            int renditionChangeIndex = 0;
            int playinIndex = networkRequest.getIndexForFirstEvent(PlayingEvent.TYPE);
            JSONArray receivedRenditionChangeEvents = new JSONArray();
            while (true) {
                renditionChangeIndex = networkRequest
                        .getIndexForNextEvent(renditionChangeIndex + 1, RenditionChangeEvent.TYPE);
                long lastRenditionChangeAt = networkRequest
                        .getCreationTimeForEvent(renditionChangeIndex) - networkRequest
                        .getCreationTimeForEvent(playinIndex);
                if (renditionChangeIndex == -1) {
                    fail("Failed to find RenditionChangeEvent dispatched after: "
                            + PLAY_PERIOD_IN_MS + " ms since playback started, with valid data"
                            + ", received events: " + receivedRenditionChangeEvents.toString());
                }
                JSONObject jo = networkRequest.getEventForIndex(renditionChangeIndex);
                receivedRenditionChangeEvents.put(jo);
                if (Math.abs(lastRenditionChangeAt - PLAY_PERIOD_IN_MS) < 500) {
                    // We found rendition change index we ware looking for, there may be more after,
                    // because I dont know how to controll the player bitadaptive settings
                    if ( !jo.has(VideoData.VIDEO_SOURCE_WIDTH) || ! jo.has(VideoData.VIDEO_SOURCE_HEIGHT)) {
                        Log.w(TAG, "Missing video width and/or video height parameters on Rendition change event, "
                                + " json: " + jo.toString());
                        continue;
                    }
                    break;
                }
            }

            JSONObject jo = networkRequest.getEventForIndex(renditionChangeIndex);
            int videoWidth = jo.getInt(VideoData.VIDEO_SOURCE_WIDTH);
            int videoHeight = jo.getInt(VideoData.VIDEO_SOURCE_HEIGHT);
            if (videoWidth != changedFmt.width && videoHeight != changedFmt.height) {
                fail("Last reported rendition change width and height (" + videoWidth + "x" +
                        videoHeight + ") do not match requested format resolution: (" +
                        changedFmt.width + "x" + changedFmt.height + ")");
            }
        } catch (Exception e) {
            fail(getExceptionFullTraceAndMessage( e ));
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
//        MuxStatsExoPlayer lMuxStats = testActivity.getMuxStats();
//        int videoWidth = lMuxStats.getSourceWidth();
//        int videoHeight = lMuxStats.getSourceHeight();
        int renditionChangeIndex = networkRequest.getIndexForFirstEvent(RenditionChangeEvent.TYPE);
        if ( renditionChangeIndex == -1 ) {
            return -1;
        }
        JSONObject jo = networkRequest.getEventForIndex(renditionChangeIndex);
        if ( jo == null || !jo.has(VideoData.VIDEO_SOURCE_WIDTH) || ! jo.has(VideoData.VIDEO_SOURCE_HEIGHT)) {
            return -1;
        }
        int videoWidth = jo.getInt(VideoData.VIDEO_SOURCE_WIDTH);
        int videoHeight = jo.getInt(VideoData.VIDEO_SOURCE_HEIGHT);

        ArrayList<Format> availableFormats = getAvailableVideoRendition();
        int index = 0;
        for (Format fmt:availableFormats) {
            if (Math.abs(fmt.width - videoWidth) < 10 &&
                    Math.abs(fmt.height - videoHeight) < 10) {
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
        selector.setParameters(selector.buildUponParameters()
                .setMaxVideoSize(fmt.width, fmt.height)
                .setForceHighestSupportedBitrate(true)
        );
    }
}
