package com.mux.stats.sdk.muxstats;

import android.content.Context;
import android.view.Surface;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MuxStatsExoPlayer extends MuxBaseExoPlayer implements Player.EventListener,
        AudioRendererEventListener, VideoRendererEventListener, AdaptiveMediaSourceEventListener,
        ExtractorMediaSource.EventListener, DefaultDrmSessionManager.EventListener,
        MetadataOutput {
    private WeakReference<AudioRendererEventListener> audioRenderListener;
    private WeakReference<VideoRendererEventListener> videoRenderListener;
    private WeakReference<AdaptiveMediaSourceEventListener> adaptiveStreamListener;
    private WeakReference<ExtractorMediaSource.EventListener> extractMediaSourceListener;
    private WeakReference<DefaultDrmSessionManager.EventListener> drmSessionListener;
    private WeakReference<MetadataOutput> metaDataListener;

    public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName, CustomerPlayerData customerPlayerData, CustomerVideoData customerVideoData) {
        super(ctx, player, playerName, customerPlayerData, customerVideoData);
        player.addListener(this);
    }

    @Override
    public void release() {
        if (this.player.get() != null) {
            this.player.get().removeListener(this);
        }
        super.release();
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
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

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        this.playWhenReady = playWhenReady;
        if (playWhenReady) {
            switch (playbackState) {
                case Player.STATE_BUFFERING:
                    if (state == PlayerState.INIT) {
                        play();
                    }
                    buffering();
                    break;
                case Player.STATE_ENDED:
                    pause();
                    break;
                case Player.STATE_READY:
                    playing();
                    break;
                case Player.STATE_IDLE:
                default:
                    // Don't care.
                    break;
            }
        } else {
            if (state != PlayerState.INIT) {
                pause();
            }
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        // Do nothing
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
        if (e.type == ExoPlaybackException.TYPE_RENDERER) {
            Exception cause = e.getRendererException();
            if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                MediaCodecRenderer.DecoderInitializationException die = (MediaCodecRenderer.DecoderInitializationException) cause;
                if (die.decoderName == null) {
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
    public void onPositionDiscontinuity(int reason) {

    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    @Override
    public void onSeekProcessed() {

    }

    @Override
    public void onAudioEnabled(DecoderCounters counters) {
        if (audioRenderListener.get() != null) {
            audioRenderListener.get().onAudioEnabled(counters);
        }
    }

    @Override
    public void onAudioSessionId(int audioSessionId) {
        if (audioRenderListener.get() != null) {
            audioRenderListener.get().onAudioSessionId(audioSessionId);
        }
    }

    @Override
    public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        if (audioRenderListener.get() != null) {
            audioRenderListener.get().onAudioDecoderInitialized(decoderName, initializedTimestampMs, initializationDurationMs);
        }
    }

    @Override
    public void onAudioInputFormatChanged(Format format) {
        if (audioRenderListener.get() != null) {
            audioRenderListener.get().onAudioInputFormatChanged(format);
        }
    }

    @Override
    public void onAudioSinkUnderrun(int bufferSize, long bufferSizeMs,
            long elapsedSinceLastFeedMs) {
        if (audioRenderListener.get() != null) {
            audioRenderListener.get().onAudioSinkUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
        }
    }

    @Override
    public void onAudioDisabled(DecoderCounters counters) {
        if (audioRenderListener.get() != null) {
            audioRenderListener.get().onAudioDisabled(counters);
        }
    }

    @Override
    public void onDrmKeysLoaded() {
        if (drmSessionListener.get() != null) {
            drmSessionListener.get().onDrmKeysLoaded();
        }
    }

    @Override
    public void onDrmSessionManagerError(Exception e) {
        internalError(new MuxErrorException(ERROR_DRM, "DrmSessionManagerError - " + e.getMessage()));
        if (drmSessionListener.get() != null) {
            drmSessionListener.get().onDrmSessionManagerError(e);
        }
    }

    @Override
    public void onDrmKeysRestored() {
        if (drmSessionListener.get() != null) {
            drmSessionListener.get().onDrmKeysRestored();
        }
    }

    @Override
    public void onDrmKeysRemoved() {
        if (drmSessionListener.get() != null) {
            drmSessionListener.get().onDrmKeysRemoved();
        }
    }

    @Override
    public void onMetadata(Metadata metadata) {
        if (metaDataListener.get() != null) {
            metaDataListener.get().onMetadata(metadata);
        }
    }

    @Override
    public void onLoadStarted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs) {
        if (adaptiveStreamListener.get() != null) {
            adaptiveStreamListener.get().onLoadStarted(dataSpec, dataType, trackType, trackFormat, trackSelectionReason, trackSelectionData, mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs);
        }
        bandwidthDispatcher.onLoadStarted(dataSpec, dataType, trackFormat, mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs);
    }

    @Override
    public void onLoadCompleted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {
        if (adaptiveStreamListener.get() != null) {
            adaptiveStreamListener.get().onLoadCompleted(dataSpec, dataType, trackType, trackFormat, trackSelectionReason, trackSelectionData, mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
        }
        bandwidthDispatcher.onLoadCompleted(dataSpec, dataType, trackFormat, mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
    }

    @Override
    public void onLoadCanceled(DataSpec dataSpec, int dataType, int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {
        if (adaptiveStreamListener.get() != null) {
            adaptiveStreamListener.get().onLoadCanceled(dataSpec, dataType, trackType, trackFormat, trackSelectionReason, trackSelectionData, mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
        }
        bandwidthDispatcher.onLoadCanceled(dataSpec);
    }

    @Override
    public void onLoadError(DataSpec dataSpec, int dataType, int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded, IOException error, boolean wasCanceled) {
        if (adaptiveStreamListener.get() != null) {
            adaptiveStreamListener.get().onLoadError(dataSpec, dataType, trackType, trackFormat, trackSelectionReason, trackSelectionData, mediaStartTimeMs, mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs, bytesLoaded, error, wasCanceled);
        }
        bandwidthDispatcher.onLoadError(dataSpec, dataType, error);
    }

    @Override
    public void onUpstreamDiscarded(int trackType, long mediaStartTimeMs, long mediaEndTimeMs) {
        if (adaptiveStreamListener.get() != null) {
            adaptiveStreamListener.get().onUpstreamDiscarded(trackType, mediaStartTimeMs, mediaEndTimeMs);
        }
    }

    @Override
    public void onDownstreamFormatChanged(int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaTimeMs) {
        mimeType = trackFormat.containerMimeType + " (" + trackFormat.sampleMimeType + ")";
        if (adaptiveStreamListener.get() != null) {
            adaptiveStreamListener.get().onDownstreamFormatChanged(trackType, trackFormat, trackSelectionReason, trackSelectionData, mediaTimeMs);
        }
    }

    @Override
    public void onLoadError(IOException e) {
        internalError(new MuxErrorException(ERROR_IO, "IOException - " + e.getMessage()));
        if (extractMediaSourceListener.get() != null) {
            extractMediaSourceListener.get().onLoadError(e);
        }
    }

    @Override
    public void onVideoEnabled(DecoderCounters counters) {
        if (videoRenderListener.get() != null) {
            videoRenderListener.get().onVideoEnabled(counters);
        }
    }

    @Override
    public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        if (videoRenderListener.get() != null) {
            videoRenderListener.get().onVideoDecoderInitialized(decoderName, initializedTimestampMs, initializationDurationMs);
        }
    }

    @Override
    public void onVideoInputFormatChanged(Format format) {
        if (videoRenderListener.get() != null) {
            videoRenderListener.get().onVideoInputFormatChanged(format);
        }
    }

    @Override
    public void onDroppedFrames(int count, long elapsedMs) {
        if (videoRenderListener.get() != null) {
            videoRenderListener.get().onDroppedFrames(count, elapsedMs);
        }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        sourceWidth = width;
        sourceHeight = height;
        if (videoRenderListener.get() != null) {
            videoRenderListener.get().onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
        }
    }

    @Override
    public void onRenderedFirstFrame(Surface surface) {
        if (videoRenderListener.get() != null) {
            videoRenderListener.get().onRenderedFirstFrame(surface);
        }
    }

    @Override
    public void onVideoDisabled(DecoderCounters counters) {
        if (videoRenderListener.get() != null) {
            videoRenderListener.get().onVideoDisabled(counters);
        }
    }

    // Special proxy listeners for ExoPlayer classes that only allow one target event listener.

    public AudioRendererEventListener getAudioRendererEventListener(AudioRendererEventListener listener) {
        audioRenderListener = new WeakReference<>(listener);
        return this;
    }

    public VideoRendererEventListener getVideoRendererEventListener(VideoRendererEventListener listener) {
        videoRenderListener = new WeakReference<>(listener);
        return this;
    }

    public AdaptiveMediaSourceEventListener getAdaptiveMediaSourceEventListener(int type, AdaptiveMediaSourceEventListener listener) {
        streamType = type;
        adaptiveStreamListener = new WeakReference<>(listener);
        return this;
    }

    public ExtractorMediaSource.EventListener getExtractorMediaSourceEventListener(int type, ExtractorMediaSource.EventListener listener) {
        streamType = type;
        extractMediaSourceListener = new WeakReference<>(listener);
        return this;
    }

    public DefaultDrmSessionManager.EventListener getDefaultDrmSessionManagerEventListener(DefaultDrmSessionManager.EventListener listener) {
        drmSessionListener = new WeakReference<>(listener);
        return this;
    }

    public MetadataOutput getMetadataRendererOutput(MetadataOutput listener) {
        metaDataListener = new WeakReference<>(listener);
        return this;
    }
}
