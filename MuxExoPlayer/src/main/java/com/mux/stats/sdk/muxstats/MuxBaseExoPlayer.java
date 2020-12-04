package com.mux.stats.sdk.muxstats;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaFormat;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.mux.stats.sdk.core.MuxSDKViewOrientation;
import com.mux.stats.sdk.core.events.EventBus;
import com.mux.stats.sdk.core.events.IEvent;
import com.mux.stats.sdk.core.events.InternalErrorEvent;
import com.mux.stats.sdk.core.events.playback.EndedEvent;
import com.mux.stats.sdk.core.events.playback.PauseEvent;
import com.mux.stats.sdk.core.events.playback.PlayEvent;
import com.mux.stats.sdk.core.events.playback.PlayingEvent;
import com.mux.stats.sdk.core.events.playback.RebufferEndEvent;
import com.mux.stats.sdk.core.events.playback.RebufferStartEvent;
import com.mux.stats.sdk.core.events.playback.RenditionChangeEvent;
import com.mux.stats.sdk.core.events.playback.RequestBandwidthEvent;
import com.mux.stats.sdk.core.events.playback.SeekedEvent;
import com.mux.stats.sdk.core.events.playback.SeekingEvent;
import com.mux.stats.sdk.core.events.playback.TimeUpdateEvent;
import com.mux.stats.sdk.core.model.BandwidthMetricData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.core.model.CustomerViewData;
import com.mux.stats.sdk.core.util.MuxLogger;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import static android.os.SystemClock.elapsedRealtime;

public class MuxBaseExoPlayer extends EventBus implements IPlayerListener {
    protected static final String TAG = "MuxStatsEventQueue";
    // Error codes start at -1 as ExoPlaybackException codes start at 0 and go up.
    protected static final int ERROR_UNKNOWN = -1;
    protected static final int ERROR_DRM = -2;
    protected static final int ERROR_IO = -3;

    protected String mimeType;
    protected Integer sourceWidth;
    protected Integer sourceHeight;
    protected Integer sourceAdvertisedBitrate;
    protected Float sourceAdvertisedFramerate;
    protected Long sourceDuration;
    protected ExoPlayerHandler playerHandler;
    protected FrameRenderedListener frameRenderedListener;
    protected Timer updatePlayheadPositionTimer;

    protected WeakReference<ExoPlayer> player;
    protected WeakReference<View> playerView;
    protected WeakReference<Context> contextRef;
    protected AdsImaSDKListener adsImaSdkListener;

    protected int streamType = -1;

    public enum PlayerState {
        BUFFERING, REBUFFERING, SEEKING, SEEKED, ERROR, PAUSED, PLAY, PLAYING, PLAYING_ADS,
        FINISHED_PLAYING_ADS, INIT, ENDED
    }
    protected PlayerState state;
    protected MuxStats muxStats;


    MuxBaseExoPlayer(Context ctx, ExoPlayer player, String playerName,
                     CustomerPlayerData customerPlayerData, CustomerVideoData customerVideoData,
                     CustomerViewData customerViewData, boolean sentryEnabled,
                     INetworkRequest networkRequest) {
        super();
        this.player = new WeakReference<>(player);
        this.contextRef = new WeakReference<>(ctx);
        state = PlayerState.INIT;
        MuxStats.setHostDevice(new MuxDevice(ctx));
        MuxStats.setHostNetworkApi(networkRequest);
        muxStats = new MuxStats(this, playerName, customerPlayerData, customerVideoData, customerViewData, sentryEnabled);
        addListener(muxStats);
        playerHandler = new ExoPlayerHandler(player.getApplicationLooper(), player);
        frameRenderedListener = new FrameRenderedListener(playerHandler);
        setPlaybackHeadUpdateInterval(false);
        try {
            adsImaSdkListener = new AdsImaSDKListener(this);
        } catch ( NoClassDefFoundError Err ) {
            Log.w(TAG, "Google Ima Ads is not included in project, using ads will be impossible !!!");
        }
    }

    /**
     * Get the instance of the IMA SDK Listener for tracking ads running through Google's
     * IMA SDK within your application.
     *
     * @deprecated
     * This method is no longer the preferred method to track Ad performance with
     * Google's IMA SDK.
     * <p> Use {@link MuxBaseExoPlayer#monitorImaAdsLoader(AdsLoader)} instead.
     * @return the IMA SDK Listener
     * @throws
     */
    @Deprecated
    public AdsImaSDKListener getIMASdkListener() {
        try {
            // Let's just check one of them
            Class.forName("com.google.ads.interactivemedia.v3.api.Ad");
            Class.forName("com.google.ads.interactivemedia.v3.api.AdErrorEvent");
            Class.forName("com.google.ads.interactivemedia.v3.api.AdEvent");
            return new AdsImaSDKListener(this);
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalStateException("IMA SDK Modules not found");
        }
    }

    /**
     * Monitor an instance of Google IMA SDK's AdsLoader
     * @param adsLoader
     *
     *
     * For ExoPlayer 2.12 AdsLoader is initialized only when the add is requested, this makes
     * this method impossible to use.
     */
	@SuppressWarnings("unused")
    public void monitorImaAdsLoader(AdsLoader adsLoader) {
        if (adsLoader == null) {
            Log.e(TAG, "Null AdsLoader provided to monitorImaAdsLoader");
            return;
        }
        try {
            // TODO: these may not be necessary, but doing it for the sake of it
            Class.forName("com.google.ads.interactivemedia.v3.api.AdsLoader");
            Class.forName("com.google.ads.interactivemedia.v3.api.AdsManager");
            Class.forName("com.google.ads.interactivemedia.v3.api.AdErrorEvent");
            Class.forName("com.google.ads.interactivemedia.v3.api.AdEvent");
            Class.forName("com.google.ads.interactivemedia.v3.api.Ad");
            final MuxBaseExoPlayer baseExoPlayer = this;
            adsLoader.addAdsLoadedListener(new AdsLoader.AdsLoadedListener() {
                @Override
                public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
                    // TODO: Add in the adresponse stuff when we can

                    // Set up the ad events that we want to use
                    AdsManager adsManager = adsManagerLoadedEvent.getAdsManager();

                    // Attach mux event and error event listeners.
                    adsManager.addAdErrorListener(adsImaSdkListener);
                    adsManager.addAdEventListener(adsImaSdkListener);
                }

                // TODO: probably need to handle some cleanup and things, like removing listeners on destroy
            });
        } catch (ClassNotFoundException cnfe) {
            return;
        }
    }

    // ExoPlayer 2.12+ need this to hook add events
    public AdErrorEvent.AdErrorListener getAdErrorEventListener() {
        return adsImaSdkListener;
    }

    // ExoPlayer 2.12+ need this to hook add events
    public AdEvent.AdEventListener getAdEventListener() {
        return adsImaSdkListener;
    }

    @SuppressWarnings("unused")
    public void updateCustomerData(CustomerPlayerData customPlayerData, CustomerVideoData customVideoData) {
        muxStats.updateCustomerData(customPlayerData, customVideoData);
    }

    @SuppressWarnings("unused")
    public void updateCustomerData(CustomerPlayerData customerPlayerData,
                                   CustomerVideoData customerVideoData,
                                   CustomerViewData customerViewData) {
        muxStats.updateCustomerData(customerPlayerData, customerVideoData, customerViewData);
    }

    @SuppressWarnings("unused")
    public CustomerVideoData getCustomerVideoData() {
        return muxStats.getCustomerVideoData();
    }

    @SuppressWarnings("unused")
    public CustomerPlayerData getCustomerPlayerData() {
        return muxStats.getCustomerPlayerData();
    }

    @SuppressWarnings("unused")
    public CustomerViewData getCustomerViewData() {
        return muxStats.getCustomerViewData();
    }

    public void enableMuxCoreDebug(boolean enable, boolean verbose) {
        muxStats.allowLogcatOutput(enable, verbose);
    }

    @SuppressWarnings("unused")
    public void videoChange(CustomerVideoData customerVideoData) {
        muxStats.videoChange(customerVideoData);
    }

    @SuppressWarnings("unused")
    public void programChange(CustomerVideoData customerVideoData) {
        muxStats.programChange(customerVideoData);
    }

    public void orientationChange(MuxSDKViewOrientation orientation) {
        muxStats.orientationChange(orientation);
    }

    public void setPlayerView(View playerView) {
        this.playerView = new WeakReference<>(playerView);
    }

    @SuppressWarnings("unused")
    public void setPlayerSize(int width, int height) {
        muxStats.setPlayerSize(width, height);
    }

    public void setScreenSize(int width, int height) {
        muxStats.setScreenSize(width, height);
    }

    public void error(MuxErrorException e) {
        muxStats.error(e);
    }

    @SuppressWarnings("unused")
    public void setAutomaticErrorTracking(boolean enabled) {
        muxStats.setAutomaticErrorTracking(enabled);
    }

    public void release() {
        muxStats.release();
        muxStats = null;
        player = null;
    }

    @SuppressWarnings("unused")
    public void setStreamType(int type) {
        streamType = type;
    }

    @Override
    public void dispatch(IEvent event) {
        if (player != null && player.get() != null && muxStats != null) {
            super.dispatch(event);
        }
    }

    @Override
    public long getCurrentPosition() {
        if (playerHandler != null)
            return playerHandler.getPlayerCurrentPosition();
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
    public Integer getSourceAdvertisedBitrate() {
        return sourceAdvertisedBitrate;
    }

    @Override
    public Float getSourceAdvertisedFramerate() {
        return sourceAdvertisedFramerate;
    }

    @Override
    public Long getSourceDuration() {
        return sourceDuration;
    }

    // State Transitions
    public PlayerState getState() {
        return state;
    }
	
    protected void configurePlaybackHeadUpdateInterval() {
        if (player == null || player.get() == null) {
            return;
        }

        TrackGroupArray trackGroups = player.get().getCurrentTrackGroups();
        boolean haveVideo = false;
        if (trackGroups.length > 0) {
            for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
                TrackGroup trackGroup = trackGroups.get(groupIndex);
                if (0 < trackGroup.length) {
                    Format trackFormat = trackGroup.getFormat(0);
                    if (trackFormat.sampleMimeType != null && trackFormat.sampleMimeType.contains("video")) {
                        haveVideo = true;
                        break;
                    }
                }
            }
        }
        setPlaybackHeadUpdateInterval(haveVideo);
    }

    protected void setPlaybackHeadUpdateInterval(boolean haveVideo) {
        if (updatePlayheadPositionTimer != null) {
            updatePlayheadPositionTimer.cancel();
        }
        if (haveVideo) {
            Player.VideoComponent videoComponent = player.get().getVideoComponent();
            videoComponent.setVideoFrameMetadataListener(frameRenderedListener);
        } else {
            // Schedule timer to execute, this is for audio only content.
            updatePlayheadPositionTimer = new Timer();
            updatePlayheadPositionTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    playerHandler.obtainMessage(ExoPlayerHandler.UPDATE_PLAYER_CURRENT_POSITION)
                            .sendToTarget();
                }
            }, 0, 15);
        }
    }

    /*
     * This will be called by AdsImaSDKListener to set the player state to: PLAYING_ADS
     * and ADS_PLAYBACK_DONE accordingly
     */
    protected void setState(PlayerState newState) {
        state = newState;
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
                return pxToDp(pv.getWidth());
            }
        }
        return 0;
    }

    @Override
    public int getPlayerViewHeight() {
        if (this.playerView != null) {
            View pv = this.playerView.get();
            if (pv != null) {
                return pxToDp(pv.getHeight());
            }
        }
        return 0;
    }

    @Override
    public boolean isPaused() {
        return state == PlayerState.PAUSED || state == PlayerState.ENDED || state == PlayerState.ERROR || state == PlayerState.INIT;
    }

    protected void buffering() {
        if (state == PlayerState.REBUFFERING || state == PlayerState.SEEKING
                || state == PlayerState.SEEKED ) {
            // ignore
            return;
        }
        // If we are going from playing to buffering then this is rebuffer event
        if (state == PlayerState.PLAYING) {
            rebufferingStarted();
            return;
        }
        // This is initial buffering event before playback starts
        state = PlayerState.BUFFERING;
        dispatch(new TimeUpdateEvent(null));
    }

    protected void pause() {
        if (state == PlayerState.REBUFFERING) {
            rebufferingEnded();
        }
        if (state == PlayerState.SEEKED) {
            dispatch(new SeekedEvent(null));
        }
        state = PlayerState.PAUSED;
        dispatch(new PauseEvent(null));
    }

    protected void play() {
        if (state == PlayerState.REBUFFERING || state == PlayerState.SEEKING
                || state == PlayerState.SEEKED ) {
            // Ignore play event after rebuffering and Seeking
            return;
        }
        state = PlayerState.PLAY;
        dispatch(new PlayEvent(null));
    }

    protected void playing() {
        if (state == PlayerState.PAUSED || state == PlayerState.FINISHED_PLAYING_ADS) {
            play();
        }
        if (state == PlayerState.REBUFFERING) {
            rebufferingEnded();
        }
        if (state == PlayerState.SEEKED) {
            dispatch(new SeekedEvent(null));
        }
        state = PlayerState.PLAYING;
        dispatch(new PlayingEvent(null));
    }


    protected void rebufferingStarted() {
        state = PlayerState.REBUFFERING;
        dispatch(new RebufferStartEvent(null));
    }

    protected void rebufferingEnded() {
        dispatch(new RebufferEndEvent(null));
    }

    protected void seeking() {
        if (state == PlayerState.PLAYING) {
            dispatch(new PauseEvent(null));
        }
        state = PlayerState.SEEKING;
        dispatch(new SeekingEvent(null));
    }

    protected void seeked() {
        /*
         * Seeked event will be fired by the player immediately after seeking event
         * This is not accurate, instead report the seeked event on first playing or pause
         * event after seeked was reported by the player.
         */
        state = PlayerState.SEEKED;
    }

    protected void ended() {
        dispatch(new PauseEvent(null));
        dispatch(new EndedEvent(null));
        state = PlayerState.ENDED;
    }

    protected void internalError(Exception error) {
        if (error instanceof MuxErrorException) {
            MuxErrorException muxError = (MuxErrorException) error;
            dispatch(new InternalErrorEvent(muxError.getCode(), muxError.getMessage()));
        } else {
            dispatch(new InternalErrorEvent(ERROR_UNKNOWN, error.getClass().getCanonicalName() + " - " + error.getMessage()));
        }
    }

    protected void handleRenditionChange(Format format) {
        if (format != null) {
            sourceAdvertisedBitrate = format.bitrate;
            if (format.frameRate > 0) {
                sourceAdvertisedFramerate = format.frameRate;
            }
            sourceWidth = format.width;
            sourceHeight = format.height;
            Log.e(TAG, "Dispatching rendition change event, w:" + sourceWidth + ", h: " + sourceHeight);
            RenditionChangeEvent event = new RenditionChangeEvent(null);
            dispatch(event);
        }
    }

    static class FrameRenderedListener implements VideoFrameMetadataListener {
        ExoPlayerHandler handler;

        public FrameRenderedListener(ExoPlayerHandler handler) {
            this.handler = handler;
        }

        // As of r2.11.x, the signature for this callback has changed. These are not annotated as @Overrides in
        // order to support both before r2.11.x and after r2.11.x at the same time.
        public void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs, Format format) {
            handler.obtainMessage(ExoPlayerHandler.UPDATE_PLAYER_CURRENT_POSITION).sendToTarget();
        }

        public void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs, Format format, @Nullable MediaFormat mediaFormat) {
            handler.obtainMessage(ExoPlayerHandler.UPDATE_PLAYER_CURRENT_POSITION).sendToTarget();
        }
    };

    static class ExoPlayerHandler extends Handler {
        static final int UPDATE_PLAYER_CURRENT_POSITION = 1;

        AtomicLong playerCurrentPosition = new AtomicLong(0);
        ExoPlayer player;

        public ExoPlayerHandler(Looper looper, ExoPlayer player) {
            super(looper);
            this.player = player;
        }

        public long getPlayerCurrentPosition() {
            return playerCurrentPosition.get();
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_PLAYER_CURRENT_POSITION:
                    playerCurrentPosition.set(player.getContentPosition());
                    break;
                default:
                    Log.e(TAG, "ExoPlayerHandler>> Unhandled message type: " + msg.what);
            }
        }
    }

    static class MuxDevice implements IDevice {
        private static final String EXO_SOFTWARE = "ExoPlayer";

        static final String CONNECTION_TYPE_CELLULAR = "cellular";
        static final String CONNECTION_TYPE_WIFI = "wifi";
        static final String CONNECTION_TYPE_ETHERNET = "ethernet";
        static final String CONNECTION_TYPE_OTHER = "other";

        protected WeakReference<Context> contextRef;
        private String deviceId;
        private String appName = "";
        private String appVersion = "";

        MuxDevice(Context ctx) {
            deviceId = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            contextRef = new WeakReference<>(ctx);
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
            return BuildConfig.MUX_PLUGIN_NAME;
        }

        @Override
        public String getPluginVersion() {
            return BuildConfig.MUX_PLUGIN_VERSION;
        }

        @Override
        public String getPlayerSoftware() {
            return EXO_SOFTWARE;
        }

        @Override
        public String getNetworkConnectionType() {
            // Checking internet connectivity
            Context context = contextRef.get();
            if (context == null) {
                return null;
            }
            ConnectivityManager connectivityMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = null;
            if (connectivityMgr != null) {
                activeNetwork = connectivityMgr.getActiveNetworkInfo();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    NetworkCapabilities nc = connectivityMgr.getNetworkCapabilities(connectivityMgr.getActiveNetwork());
                    if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        return CONNECTION_TYPE_ETHERNET;
                    } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return CONNECTION_TYPE_WIFI;
                    } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        return CONNECTION_TYPE_CELLULAR;
                    } else {
                        return CONNECTION_TYPE_OTHER;
                    }
                } else {
                    if (activeNetwork.getType() == ConnectivityManager.TYPE_ETHERNET) {
                        return CONNECTION_TYPE_ETHERNET;
                    } else if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                        return CONNECTION_TYPE_WIFI;
                    } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                        return CONNECTION_TYPE_CELLULAR;
                    } else {
                        return CONNECTION_TYPE_OTHER;
                    }
                }
            }
            return null;
        }

        @Override
        public long getElapsedRealtime() {
            return elapsedRealtime();
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
                long bytesLoaded, Map<String, List<String>> responseHeaders) {
            if (player == null || player.get() == null || muxStats == null || currentBandwidthMetric() == null) {
                return;
            }
            BandwidthMetricData loadData = currentBandwidthMetric().onLoadCompleted(dataSpec,
                    dataType,
                    trackFormat, mediaStartTimeMs,
                    mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs, bytesLoaded);

            // Only append this data if we have some load data going on already
            // TODO - this does not work correctly today, fix this and re-enable it.
//            if (loadData != null && responseHeaders != null) {
//                Hashtable<String, String> headers = parseHeaders(responseHeaders);
//
//                if (headers != null) {
//                    loadData.setRequestResponseHeaders(headers);
//                }
//            }

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

        private Hashtable<String, String> parseHeaders(Map<String, List<String>> responseHeaders) {
            if (responseHeaders == null || responseHeaders.size() == 0) {
                return null;
            }

            Hashtable<String, String> headers = new Hashtable<String, String>();
            for (String headerName : responseHeaders.keySet()) {
                if (headerName == null) {
                    continue;
                }
                List<String> headerValues = responseHeaders.get(headerName);
                if (headerValues.size() == 1) {
                    headers.put(headerName, headerValues.get(0));
                } else if (headerValues.size() > 1) {
                    // In the case that there is more than one header, we squash
                    // it down to a single comma-separated value per RFC 2616
                    // https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2
                    String headerValue = headerValues.get(0);
                    for (int i = 1; i < headerValues.size(); i++) {
                        headerValue = headerValue + ", " + headerValues.get(i);
                    }
                    headers.put(headerName, headerValue);
                }
            }
            return headers;
        }
    }

    private int pxToDp(int px) {
        Context context = contextRef.get();

        // Bail out if we don't have the context
        if (context == null) {
            MuxLogger.d(TAG, "Error retrieving Context for logical resolution, using physical");
            return px;
        }

        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int dp = Math.round(px / (displayMetrics.xdpi / displayMetrics.densityDpi));
        return dp;
    }

    protected BandwidthMetricDispatcher bandwidthDispatcher = new BandwidthMetricDispatcher();
    protected List<BandwidthMetricData.Rendition> renditionList;
}
