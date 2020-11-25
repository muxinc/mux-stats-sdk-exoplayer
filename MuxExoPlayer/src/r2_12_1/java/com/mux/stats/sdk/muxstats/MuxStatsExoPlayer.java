package com.mux.stats.sdk.muxstats;

import android.content.Context;
import android.view.Surface;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.mux.stats.sdk.core.events.playback.SeekedEvent;
import com.mux.stats.sdk.core.events.playback.SeekingEvent;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.core.model.CustomerViewData;

import java.io.IOException;

public class MuxStatsExoPlayer extends MuxBaseExoPlayer implements AnalyticsListener, Player.EventListener{

    public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
                             CustomerPlayerData customerPlayerData,
                             CustomerVideoData customerVideoData) {
        this(ctx, player, playerName, customerPlayerData, customerVideoData, null, true);
    }

    public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
                             CustomerPlayerData customerPlayerData,
                             CustomerVideoData customerVideoData,
                             CustomerViewData customerViewData) {
        this(ctx, player, playerName, customerPlayerData, customerVideoData, customerViewData, true);
    }

    public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
                             CustomerPlayerData customerPlayerData,
                             CustomerVideoData customerVideoData,
                             boolean sentryEnabled) {
        this(ctx, player, playerName, customerPlayerData, customerVideoData, null, sentryEnabled);
    }

    public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
                             CustomerPlayerData customerPlayerData,
                             CustomerVideoData customerVideoData,
                             CustomerViewData customerViewData, boolean sentryEnabled) {
        super(ctx, player, playerName, customerPlayerData, customerVideoData, customerViewData, sentryEnabled);

        if (player instanceof SimpleExoPlayer) {
            ((SimpleExoPlayer) player).addAnalyticsListener(this);
        } else {
            player.addListener(this);
        }
        if (player.getPlaybackState() == Player.STATE_BUFFERING) {
            // playback started before muxStats was initialized
            play();
            buffering();
        } else if (player.getPlaybackState() == Player.STATE_READY) {
            // We have to simulate all the events we expect to see here, even though not ideal
            play();
            buffering();
            playing();
        }
    }

    @Override
    public void release() {
        if (this.player.get() != null) {
            ExoPlayer player = this.player.get();
            if (player instanceof SimpleExoPlayer) {
                ((SimpleExoPlayer) player).removeAnalyticsListener(this);
            } else {
                player.removeListener(this);
            }
        }
        super.release();
    }

    // ------BEGIN AnalyticsListener callbacks------
    @Override
    public void onAudioAttributesChanged(AnalyticsListener.EventTime eventTime,
                                         AudioAttributes audioAttributes) { }

    @Override
    public void onAudioDecoderInitialized​(AnalyticsListener.EventTime eventTime,
                                          String decoderName, long initializationDurationMs) { }

    @Override
    public void onAudioDisabled​(AnalyticsListener.EventTime eventTime, DecoderCounters counters) { }

    @Override
    public void onAudioEnabled​(AnalyticsListener.EventTime eventTime, DecoderCounters counters) { }

    @Override
    public void onAudioInputFormatChanged​(AnalyticsListener.EventTime eventTime, Format format) { }

    @Override
    public void onAudioPositionAdvancing​(AnalyticsListener.EventTime eventTime,
                                         long playoutStartSystemTimeMs) { }

    @Override
    public void onAudioSessionId(AnalyticsListener.EventTime eventTime, int audioSessionId) { }

    @Override
    public void onAudioUnderrun(AnalyticsListener.EventTime eventTime, int bufferSize,
                                long bufferSizeMs, long elapsedSinceLastFeedMs) { }

    @Override
    public void onBandwidthEstimate​(AnalyticsListener.EventTime eventTime, int totalLoadTimeMs,
                                    long totalBytesLoaded, long bitrateEstimate) { }

    // Note: onDecoderDisabled, onDecoderEnabled, onDecoderInitialized, and
    // onDecoderInputFormatChanged were deprecated by 2.12.0, so they're not included here.

    @Override
    public void onDownstreamFormatChanged(AnalyticsListener.EventTime eventTime,
                                          MediaLoadData mediaLoadData) {
        if (mediaLoadData.trackFormat != null && mediaLoadData.trackFormat.containerMimeType != null) {
            mimeType = mediaLoadData.trackFormat.containerMimeType;
        }
    }

    @Override
    public void onDrmKeysLoaded(AnalyticsListener.EventTime eventTime) { }

    @Override
    public void onDrmKeysRemoved(AnalyticsListener.EventTime eventTime) { }

    @Override
    public void onDrmKeysRestored(AnalyticsListener.EventTime eventTime) { }

    @Override
    public void	onDrmSessionAcquired​(AnalyticsListener.EventTime eventTime) { }

    @Override
    public void onDrmSessionManagerError(AnalyticsListener.EventTime eventTime, Exception e) {
        internalError(new MuxErrorException(ERROR_DRM, "DrmSessionManagerError - " + e.getMessage()));
    }

    @Override
    public void onDrmSessionReleased​(AnalyticsListener.EventTime eventTime) { }

    @Override
    public void onDroppedVideoFrames​(AnalyticsListener.EventTime eventTime, int droppedFrames,
                                     long elapsedMs) { }

    // Note: onLoadingChanged was deprecated and moved to onIsLoadingChanged in 2.12.0
    @Override
    public void onIsLoadingChanged(AnalyticsListener.EventTime eventTime, boolean isLoading) {
        onIsLoadingChanged(isLoading);
    }

    @Override
    public void onIsPlayingChanged(AnalyticsListener.EventTime eventTime, boolean isPlaying) { }

    @Override
    public void onLoadCanceled(AnalyticsListener.EventTime eventTime,
                               LoadEventInfo loadEventInfo,
                               MediaLoadData mediaLoadData) {
        bandwidthDispatcher.onLoadCanceled(loadEventInfo.dataSpec);
    }

    @Override
    public void onLoadCompleted(AnalyticsListener.EventTime eventTime,
                                LoadEventInfo loadEventInfo,
                                MediaLoadData mediaLoadData) {
        bandwidthDispatcher.onLoadCompleted(loadEventInfo.dataSpec, mediaLoadData.dataType,
                mediaLoadData.trackFormat, mediaLoadData.mediaStartTimeMs,
                mediaLoadData.mediaEndTimeMs, loadEventInfo.elapsedRealtimeMs,
                loadEventInfo.loadDurationMs, loadEventInfo.bytesLoaded,
                loadEventInfo.responseHeaders);
    }

    @Override
    public void onLoadError(AnalyticsListener.EventTime eventTime,
                            LoadEventInfo loadEventInfo,
                            MediaLoadData mediaLoadData, IOException e,
                            boolean wasCanceled) {
        bandwidthDispatcher.onLoadError(loadEventInfo.dataSpec, mediaLoadData.dataType, e);
    }

    @Override
    public void onLoadStarted(AnalyticsListener.EventTime eventTime,
                              LoadEventInfo loadEventInfo,
                              MediaLoadData mediaLoadData) {
        bandwidthDispatcher.onLoadStarted(loadEventInfo.dataSpec, mediaLoadData.dataType,
                mediaLoadData.trackFormat, mediaLoadData.mediaStartTimeMs,
                mediaLoadData.mediaEndTimeMs, loadEventInfo.elapsedRealtimeMs);
    }

    @Override
    public void onMediaItemTransition​(AnalyticsListener.EventTime eventTime, MediaItem mediaItem,
                                      int reason) { }

    @Override
    public void onMetadata(AnalyticsListener.EventTime eventTime, Metadata metadata) { }

    @Override
    public void onPlaybackParametersChanged(AnalyticsListener.EventTime eventTime,
                                            PlaybackParameters playbackParameters) {
        onPlaybackParametersChanged(playbackParameters);
    }

    // Note: onPlayerStateChanged was deprecated in 2.12.0, replaced with onPlayWhenReadyChanged
    // and onPlaybackStateChanged.
    @Override
    public void onPlaybackStateChanged​(AnalyticsListener.EventTime eventTime, int state) {
        onPlaybackStateChanged(state);
    }

    @Override
    public void onPlaybackSuppressionReasonChanged(AnalyticsListener.EventTime eventTime,
                                                   int playbackSuppressionReason) { }

    @Override
    public void onPlayerError(AnalyticsListener.EventTime eventTime, ExoPlaybackException error) {
        onPlayerError(error);
    }

    @Override
    public void onPlayWhenReadyChanged(AnalyticsListener.EventTime eventTime, boolean playWhenReady,
                                       int reason) {
        onPlayWhenReadyChanged(playWhenReady, reason);
    }

    @Override
    public void onPositionDiscontinuity(AnalyticsListener.EventTime eventTime, int reason) {
        onPositionDiscontinuity(reason);
    }

    @Override
    public void onRenderedFirstFrame(AnalyticsListener.EventTime eventTime, Surface surface) { }

    @Override
    public void onRepeatModeChanged(AnalyticsListener.EventTime eventTime, int repeatMode) {
        onRepeatModeChanged(repeatMode);
    }

    // Note: onSeekProcessed was deprecated in 2.12.0

    @Override
    public void onSeekStarted(AnalyticsListener.EventTime eventTime) {
        dispatch(new SeekingEvent(null));
    }

    @Override
    public void onShuffleModeChanged(AnalyticsListener.EventTime eventTime,
                                     boolean shuffleModeEnabled) {
        onShuffleModeEnabledChanged(shuffleModeEnabled);
    }

    @Override
    public void onSkipSilenceEnabledChanged​(AnalyticsListener.EventTime eventTime,
                                            boolean skipSilenceEnabled) { }

    @Override
    public void onSurfaceSizeChanged(AnalyticsListener.EventTime eventTime, int width,
                                     int height) { }

    @Override
    public void onTimelineChanged(AnalyticsListener.EventTime eventTime, int reason) {
        onTimelineChanged(eventTime.timeline, reason);
    }

    @Override
    public void onTracksChanged(AnalyticsListener.EventTime eventTime, TrackGroupArray trackGroups,
                                TrackSelectionArray trackSelections) {
        onTracksChanged(trackGroups, trackSelections);
    }

    @Override
    public void onUpstreamDiscarded(EventTime eventTime, MediaLoadData mediaLoadData) { }

    @Override
    public void onVideoDecoderInitialized​(AnalyticsListener.EventTime eventTime,
                                          String decoderName, long initializationDurationMs) { }

    @Override
    public void onVideoDisabled​(AnalyticsListener.EventTime eventTime, DecoderCounters counters) { }

    @Override
    public void onVideoEnabled​(AnalyticsListener.EventTime eventTime, DecoderCounters counters) { }

    @Override
    public void onVideoFrameProcessingOffset​(AnalyticsListener.EventTime eventTime,
                                             long totalProcessingOffsetUs, int frameCount) { }

    @Override
    public void onVideoInputFormatChanged​(AnalyticsListener.EventTime eventTime, Format format) {
        handleRenditionChange(format);
    }

    @Override
    public void onVideoSizeChanged(AnalyticsListener.EventTime eventTime, int width, int height,
                                   int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        sourceWidth = width;
        sourceHeight = height;
    }

    @Override
    public void onVolumeChanged(AnalyticsListener.EventTime eventTime, float volume) { }
    // ------END AnalyticsListener callbacks------

    // ------BEGIN Player.EventListener callbacks------
    @Override
    public void onExperimentalOffloadSchedulingEnabledChanged​(boolean offloadSchedulingEnabled) { }

    // Note: As of 2.12.0, onLoadingChanged was deprecated, replaced with onIsLoadingChanged
    @Override
    public void onIsLoadingChanged(boolean isLoading) { }

    @Override
    public void onIsPlayingChanged​(boolean isPlaying) { }

    @Override
    public void onMediaItemTransition​(MediaItem mediaItem, int reason) { }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) { }

    // Note: onPlayerStateChanged was deprecated and replaced with
    // onPlayWhenReadyChanged and onPlaybackStateChanged in 2.12.0
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        PlayerState state = this.getState();
        if (state == PlayerState.PLAYING_ADS) {
            // Ignore all normal events while playing ads !!!
            return;
        }
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                // We have entered buffering
                buffering();
                // If we are expected to playWhenReady, signal the play event
                if (playWhenReady) {
                    play();
                } else if (state != PlayerState.PAUSED) {
                    pause();
                }
                break;
            case Player.STATE_ENDED:
                ended();
                break;
            case Player.STATE_READY:
                // By the time we get here, it depends on playWhenReady to know if we're playing
                if (playWhenReady) {
                    playing();
                } else if (state != PlayerState.PAUSED) {
                    pause();
                }
                break;
            case Player.STATE_IDLE:
            default:
                // don't care
                break;
        }
    }

    @Override
    public void onPlaybackSuppressionReasonChanged​(int playbackSuppressionReason) { }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
        if (e.type == ExoPlaybackException.TYPE_RENDERER) {
            Exception cause = e.getRendererException();
            if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                MediaCodecRenderer.DecoderInitializationException die = (MediaCodecRenderer.DecoderInitializationException) cause;
                if (die.codecInfo != null && die.codecInfo.name == null) {
                    if (die.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                        internalError(new MuxErrorException(e.type, "Unable to query device decoders"));
                    } else if (die.secureDecoderRequired) {
                        internalError(new MuxErrorException(e.type, "No secure decoder for " + die.mimeType));
                    } else {
                        internalError(new MuxErrorException(e.type, "No decoder for " + die.mimeType));
                    }
                } else {
                    internalError(new MuxErrorException(e.type, "Unable to instantiate decoder for " + die.mimeType));
                }
            }
        } else if (e.type == ExoPlaybackException.TYPE_SOURCE) {
            Exception error = e.getSourceException();
            internalError(new MuxErrorException(e.type, error.getClass().getCanonicalName() + " - " + error.getMessage()));
        } else if (e.type == ExoPlaybackException.TYPE_UNEXPECTED) {
            Exception error = e.getUnexpectedException();
            internalError(new MuxErrorException(e.type, error.getClass().getCanonicalName() + " - " + error.getMessage()));
        } else {
            internalError(e);
        }
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        this.playWhenReady = playWhenReady;
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            dispatch(new SeekedEvent(null));
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) { }

    // Note, onSeekProcessed was deprecated in 2.12.0

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) { }

    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
        if (timeline != null && timeline.getWindowCount() > 0) {
            Timeline.Window window = new Timeline.Window();
            timeline.getWindow(0, window);
            sourceDuration = window.getDurationMs();
        }
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        bandwidthDispatcher.onTracksChanged(trackGroups);
    }
}
