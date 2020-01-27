package com.google.android.exoplayer2.demo;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;

import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.muxstats.MuxStatsExoPlayer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RequestHeaderParser implements MediaSourceEventListener {

    static final String TAG = "RequestHeaderParser";

    MuxStatsExoPlayer muxStats;
    ArrayList<String> cdnHeaderValues = new ArrayList<>();
    boolean isCDNParsed = false;

    public RequestHeaderParser(MuxStatsExoPlayer muxStats) {
        this.muxStats = muxStats;
        cdnHeaderValues.add("server");
    }

    @Override
    public void onMediaPeriodCreated(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {

    }

    @Override
    public void onMediaPeriodReleased(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {

    }

    @Override
    public void onLoadStarted(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {

    }

    @Override
    public void onLoadCompleted(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
//        printHeaders(loadEventInfo.responseHeaders);
        // This handler is called many times, we only need to parse CDN once
        if (!isCDNParsed) {
            parseHeaders(loadEventInfo.responseHeaders);
        }
    }

    @Override
    public void onLoadCanceled(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {

    }

    @Override
    public void onLoadError(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {

    }

    @Override
    public void onReadingStarted(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {

    }

    @Override
    public void onUpstreamDiscarded(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {

    }

    @Override
    public void onDownstreamFormatChanged(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {

    }

    private void printHeaders(Map<String, List<String>> responseHeaders) {
        Log.i(TAG, "\n\n\nRequestHeaders:\n");
        for (String header : responseHeaders.keySet()) {
            Log.i(TAG, "    " + header + ":");
            for (String line : responseHeaders.get(header)) {
                Log.i(TAG, "        " + line);
            }
        }
        Log.i(TAG, "RequestHeaders DONE !!!\n\n\n");
    }

    private void parseHeaders(Map<String, List<String>> responseHeaders) {
        for (String header : responseHeaders.keySet()) {
            if (header != null && cdnHeaderValues.contains(header.toLowerCase())) {
                String cdn = "";
                for (String line : responseHeaders.get(header)) {
                    // Should we add newline character here if there is more then one line.
                    cdn +=  line;
                }
                CustomerVideoData videoData = muxStats.getCustomerVideoData();
                CustomerPlayerData playerData = muxStats.getCustomerPlayerData();
                videoData.setVideoCdn(cdn);
                muxStats.updateCustomerDataForPlayer(playerData, videoData);
                isCDNParsed = true;
            }
        }
    }
}
