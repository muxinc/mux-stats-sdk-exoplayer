package com.mux.stats.sdk.muxstats;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.mux.stats.sdk.core.events.EventBus;
import com.mux.stats.sdk.core.events.IEvent;
import com.mux.stats.sdk.core.events.InternalErrorEvent;
import com.mux.stats.sdk.core.events.playback.PauseEvent;
import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.RequestBandwidthEvent;
import com.mux.stats.sdk.core.events.playback.TimeUpdateEvent;
import com.mux.stats.sdk.core.model.BandwidthMetricData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.core.util.MuxLogger;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MuxBaseExoPlayer extends EventBus implements IPlayerListener {
    protected static final String TAG = "MuxStatsListener";
    // Error codes start at -1 as ExoPlaybackException codes start at 0 and go up.
    protected static final int ERROR_UNKNOWN = -1;
    protected static final int ERROR_DRM = -2;
    protected static final int ERROR_IO = -3;
    protected boolean playWhenReady;

    protected String mimeType;
    protected Integer sourceWidth;
    protected Integer sourceHeight;
    protected Long sourceDuration;

    protected WeakReference<ExoPlayer> player;
    protected WeakReference<View> playerView;

    protected int streamType = -1;

    public enum PlayerState {
        BUFFERING, ERROR, PAUSED, PLAY, PLAYING, INIT
    }
    protected PlayerState state;
    protected MuxStats muxStats;

    MuxBaseExoPlayer(Context ctx, ExoPlayer player, String playerName, CustomerPlayerData customerPlayerData, CustomerVideoData customerVideoData) {
        super();
        this.player = new WeakReference<>(player);
        state = PlayerState.INIT;
        MuxStats.setHostDevice(new MuxDevice(ctx));
        MuxStats.setHostNetworkApi(new MuxNetworkRequests());
        muxStats = new MuxStats(this, playerName, customerPlayerData, customerVideoData);
        addListener(muxStats);
    }

    public AdsImaSDKListener getIMASdkListener() {
        return new AdsImaSDKListener(this);
    }

    public void videoChange(CustomerVideoData customerVideoData) {
        muxStats.videoChange(customerVideoData);
    }

    public void programChange(CustomerVideoData customerVideoData) {
        muxStats.programChange(customerVideoData);
    }

    public void setPlayerView(View playerView) {
        this.playerView = new WeakReference<>(playerView);
    }

    public void setPlayerSize(int width, int height) {
        muxStats.setPlayerSize(width, height);
    }

    public void setScreenSize(int width, int height) {
        muxStats.setScreenSize(width, height);
    }

    public void error(MuxErrorException e) {
        muxStats.error(e);
    }

    public void setAutomaticErrorTracking(boolean enabled) {
        muxStats.setAutomaticErrorTracking(enabled);
    }

    public void release() {
        muxStats.release();
        muxStats = null;
        player = null;
    }

    public void setStreamType(int type) {
        streamType = type;
    }

    @Override
    public void dispatch(IEvent event) {
        if (player != null && player.get() != null && muxStats != null)
            super.dispatch(event);
    }

    @Override
    public long getCurrentPosition() {
        if (player != null && player.get() != null)
            return player.get().getCurrentPosition();
        return 0;
    }

    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public Integer getSourceWidth() {
        return sourceWidth;
    }

    @Override
    public Integer getSourceHeight() {
        return sourceHeight;
    }

    @Override
    public Long getSourceDuration() {
        return sourceDuration;
    }

    // State Transitions
    public PlayerState getState() {
        return state;
    }

    @Override
    public boolean isBuffering() {
        return getState() == MuxBaseExoPlayer.PlayerState.BUFFERING;
    }

    @Override
    public int getPlayerViewWidth() {
        if (this.playerView != null) {
            View pv = this.playerView.get();
            if (pv != null) {
                return pv.getWidth();
            }
        }
        return 0;
    }

    @Override
    public int getPlayerViewHeight() {
        if (this.playerView != null) {
            View pv = this.playerView.get();
            if (pv != null) {
                return pv.getHeight();
            }
        }
        return 0;
    }

    @Override
    public boolean isPaused() {
        return !playWhenReady;
    }

    protected void buffering() {
        state = PlayerState.BUFFERING;
        dispatch(new TimeUpdateEvent(null));
    }

    protected void pause() {
        state = PlayerState.PAUSED;
        dispatch(new PauseEvent(null));
    }

    protected void play() {
        state = PlayerState.PLAY;
        dispatch(new PlayEvent(null));
    }

    protected void playing() {
        if (state ==  PlayerState.PAUSED) {
            play();
        }
        state = PlayerState.PLAYING;
        dispatch(new PlayingEvent(null));
    }

    protected void internalError(Exception error) {
        if (error instanceof MuxErrorException) {
            MuxErrorException muxError = (MuxErrorException) error;
            dispatch(new InternalErrorEvent(muxError.getCode(), muxError.getMessage()));
        } else {
            dispatch(new InternalErrorEvent(ERROR_UNKNOWN, error.getClass().getCanonicalName() + " - " + error.getMessage()));
        }
    }

    static class MuxDevice implements IDevice {
        private static final String MUX_PLUGIN_NAME = "android-mux";
        private static final String MUX_PLUGIN_VERSION = "0.4.4";
        private static final String EXO_SOFTWARE = "ExoPlayer";

        private String deviceId;
        private String appName = "";
        private String appVersion = "";

        MuxDevice(Context ctx) {
            deviceId = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            try {
                PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                appName = pi.packageName;
                appVersion = pi.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                MuxLogger.d(TAG, "could not get package info");
            }
        }

        @Override
        public String getHardwareArchitecture() {
            return Build.HARDWARE;
        }

        @Override
        public String getOSFamily() {
            return "Android";
        }

        @Override
        public String getOSVersion() {
            return Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")";
        }

        @Override
        public String getManufacturer() {
            return Build.MANUFACTURER;
        }

        @Override
        public String getModelName() {
            return Build.MODEL;
        }

        @Override
        public String getPlayerVersion() {
            return ExoPlayerLibraryInfo.VERSION;
        }

        @Override
        public String getDeviceId() {
            return deviceId;
        }

        @Override
        public String getAppName() {
            return appName;
        }

        @Override
        public String getAppVersion() {
            return appVersion;
        }

        @Override
        public String getPluginName() {
            return MUX_PLUGIN_NAME;
        }

        @Override
        public String getPluginVersion() {
            return MUX_PLUGIN_VERSION;
        }

        @Override
        public String getPlayerSoftware() {
            return EXO_SOFTWARE;
        }

        @Override
        public void outputLog(String tag, String msg) {
            Log.v(tag, msg);
        }
    }

    class BandwidthMetric {
        public BandwidthMetricData onLoadError(DataSpec dataSpec, int dataType, IOException e) {
            BandwidthMetricData loadData = new BandwidthMetricData();
            loadData.setRequestError(e.toString());
            if (dataSpec != null && dataSpec.uri != null) {
                loadData.setRequestUrl(dataSpec.uri.toString());
                loadData.setRequestHostName(dataSpec.uri.getHost());
            }
            switch (dataType) {
                case C.DATA_TYPE_MANIFEST:
                    loadData.setRequestType("manifest");
                    break;
                case C.DATA_TYPE_MEDIA:
                    loadData.setRequestType("media");
                    break;
                default:
                    return null;
            }
            loadData.setRequestErrorCode(null);
            loadData.setRequestErrorText(e.getMessage());
            return loadData;
        }

        public BandwidthMetricData onLoadCanceled(DataSpec dataSpec) {
            BandwidthMetricData loadData = new BandwidthMetricData();
            loadData.setRequestCancel("genericLoadCanceled");
            if (dataSpec != null && dataSpec.uri != null) {
                loadData.setRequestUrl(dataSpec.uri.toString());
                loadData.setRequestHostName(dataSpec.uri.getHost());
            }
            loadData.setRequestType("media");
            return loadData;
        }

        protected BandwidthMetricData onLoad(DataSpec dataSpec, int dataType,
                Format trackFormat, long mediaStartTimeMs, long mediaEndTimeMs,
                long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {
            BandwidthMetricData loadData = new BandwidthMetricData();
            if (bytesLoaded > 0) {
                loadData.setRequestBytesLoaded(bytesLoaded);
            }
            switch (dataType) {
                case C.DATA_TYPE_MANIFEST:
                    loadData.setRequestType("manifest");
                    break;
                case C.DATA_TYPE_MEDIA:
                    loadData.setRequestType("media");
                    break;
                default:
                    return null;
            }
            loadData.setRequestResponseHeaders(null);
            if (dataSpec != null && dataSpec.uri != null) {
                loadData.setRequestHostName(dataSpec.uri.getHost());
            }
            if (dataType == C.DATA_TYPE_MEDIA) {
                loadData.setRequestMediaDuration(mediaEndTimeMs - mediaStartTimeMs);
            }
            if (trackFormat != null) {
                loadData.setRequestCurrentLevel(null);
                if (dataType == C.DATA_TYPE_MEDIA) {
                    loadData.setRequestMediaStartTime(mediaStartTimeMs);
                }
                loadData.setRequestVideoWidth(trackFormat.width);
                loadData.setRequestVideoHeight(trackFormat.height);
            }
            loadData.setRequestRenditionLists(renditionList);
            return loadData;
        }

        public BandwidthMetricData onLoadStarted(DataSpec dataSpec, int dataType,
                Format trackFormat, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs) {
            BandwidthMetricData loadData = onLoad(dataSpec, dataType, trackFormat,
                    mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs, 0, 0);
            if (loadData != null) {
                loadData.setRequestResponseStart(elapsedRealtimeMs);
            }
            return loadData;
        }

        public BandwidthMetricData onLoadCompleted(DataSpec dataSpec, int dataType,
                Format trackFormat, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs,
                long loadDurationMs, long bytesLoaded) {
            BandwidthMetricData loadData = onLoad(dataSpec, dataType, trackFormat,
                    mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
            if (loadData != null) {
                loadData.setRequestResponseStart(elapsedRealtimeMs - loadDurationMs);
                loadData.setRequestResponseEnd(elapsedRealtimeMs);
            }
            return loadData;
        }
    }

    class BandwidthMetricHls extends BandwidthMetric {
        @Override
        public BandwidthMetricData onLoadError(DataSpec dataSpec, int dataType, IOException e) {
            BandwidthMetricData loadData = super.onLoadError(dataSpec, C.DATA_TYPE_MEDIA, e);
            return loadData;
        }

        @Override
        public BandwidthMetricData onLoadCanceled(DataSpec dataSpec) {
            BandwidthMetricData loadData = super.onLoadCanceled(dataSpec);
            loadData.setRequestCancel("hlsFragLoadEmergencyAborted");
            return loadData;
        }

        @Override
        public BandwidthMetricData onLoadCompleted(DataSpec dataSpec, int dataType,
                Format trackFormat, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs,
                long loadDurationMs, long bytesLoaded) {
            BandwidthMetricData loadData = super.onLoadCompleted(dataSpec, dataType, trackFormat,
                    mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
            if (loadData != null) {
                switch (dataType) {
                    case C.DATA_TYPE_MANIFEST:
                        loadData.setRequestEventType("hlsManifestLoaded");
                        break;
                    case C.DATA_TYPE_MEDIA:
                        loadData.setRequestEventType("hlsFragBuffered");
                        break;
                    default:
                        break;
                }
                if (trackFormat != null)
                    loadData.setRequestLabeledBitrate(trackFormat.bitrate);
            }
            return loadData;
        }
    }

    class BandwidthMetricDash extends BandwidthMetric {
        @Override
        public BandwidthMetricData onLoadStarted(DataSpec dataSpec, int dataType,
                Format trackFormat, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs) {
            BandwidthMetricData loadData = super.onLoadStarted(dataSpec, dataType, trackFormat,
                    mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs);
            if (loadData != null) {
                switch (dataType) {
                    case C.DATA_TYPE_MEDIA:
                        loadData.setRequestEventType("initFragmentLoaded");
                        break;
                    default:
                        break;
                }
            }
            return loadData;
        }

        @Override
        public BandwidthMetricData onLoadCompleted(DataSpec dataSpec, int dataType,
                Format trackFormat, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs,
                long loadDurationMs, long bytesLoaded) {
            BandwidthMetricData loadData = super.onLoadCompleted(dataSpec, dataType, trackFormat,
                    mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
            if (loadData != null) {
                switch (dataType) {
                    case C.DATA_TYPE_MANIFEST:
                        loadData.setRequestEventType("manifestLoaded");
                        break;
                    case C.DATA_TYPE_MEDIA:
                        loadData.setRequestEventType("mediaFragmentLoaded");
                        break;
                    default:
                        break;
                }
            }
            return loadData;
        }
    }

    class BandwidthMetricDispatcher {
        private BandwidthMetric bandwidthMetricHls = new BandwidthMetricHls();
        private BandwidthMetric bandwidthMetricDash = new BandwidthMetricDash();

        public BandwidthMetric currentBandwidthMetric() {
            switch(streamType) {
                case C.TYPE_HLS:
                    return bandwidthMetricHls;
                case C.TYPE_DASH:
                    return bandwidthMetricDash;
                default:
                    break;
            }
            return null;
        }

        public void onLoadError(DataSpec dataSpec, int dataType, IOException e) {
            if (player == null || player.get() == null || muxStats == null || currentBandwidthMetric() == null) {
                return;
            }
            BandwidthMetricData loadData = currentBandwidthMetric().onLoadError(dataSpec, dataType, e);
            dispatch(loadData);
        }

        public void onLoadCanceled(DataSpec dataSpec) {
            if (player == null || player.get() == null || muxStats == null || currentBandwidthMetric() == null) {
                return;
            }
            BandwidthMetricData loadData = currentBandwidthMetric().onLoadCanceled(dataSpec);
            dispatch(loadData);
        }

        public void onLoadStarted(DataSpec dataSpec, int dataType, Format trackFormat,
                long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs) {
            if (player == null || player.get() == null || muxStats == null || currentBandwidthMetric() == null) {
                return;
            }
            currentBandwidthMetric().onLoadStarted(dataSpec, dataType,
                    trackFormat, mediaStartTimeMs,
                    mediaEndTimeMs, elapsedRealtimeMs);
        }

        public void onLoadCompleted(DataSpec dataSpec, int dataType, Format trackFormat,
                long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs,
                long bytesLoaded) {
            if (player == null || player.get() == null || muxStats == null || currentBandwidthMetric() == null) {
                return;
            }
            BandwidthMetricData loadData = currentBandwidthMetric().onLoadCompleted(dataSpec,
                    dataType,
                    trackFormat, mediaStartTimeMs,
                    mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
            dispatch(loadData);
        }

        public void onTracksChanged(TrackGroupArray trackGroups) {
            if (player == null || player.get() == null || muxStats == null || currentBandwidthMetric() == null) {
                return;
            }
            if (trackGroups.length > 0) {
                for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
                    TrackGroup trackGroup = trackGroups.get(groupIndex);
                    if (0 < trackGroup.length) {
                        Format trackFormat = trackGroup.getFormat(0);
                        if (trackFormat.containerMimeType != null && trackFormat.containerMimeType.contains("video")) {
                            List<BandwidthMetricData.Rendition> renditions = new ArrayList<>();
                            for (int i = 0; i < trackGroup.length; i++) {
                                trackFormat = trackGroup.getFormat(i);
                                BandwidthMetricData.Rendition rendition = new BandwidthMetricData.Rendition();
                                rendition.bitrate = trackFormat.bitrate;
                                rendition.width = trackFormat.width;
                                rendition.height = trackFormat.height;
                                renditions.add(rendition);
                            }
                            renditionList = renditions;
                        }
                    }
                }
            }
        }

        private void dispatch(BandwidthMetricData data) {
            if (data != null) {
                RequestBandwidthEvent playback = new RequestBandwidthEvent(null);
                playback.setBandwidthMetricData(data);
                MuxBaseExoPlayer.this.dispatch(playback);
            }
        }
    }

    protected BandwidthMetricDispatcher bandwidthDispatcher = new BandwidthMetricDispatcher();
    protected List<BandwidthMetricData.Rendition> renditionList;
}
