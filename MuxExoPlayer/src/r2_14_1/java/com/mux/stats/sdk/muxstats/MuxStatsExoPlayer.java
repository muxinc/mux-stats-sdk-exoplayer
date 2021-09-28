package com.mux.stats.sdk.muxstats;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason;
import com.google.android.exoplayer2.Player.TimelineChangeReason;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderException;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.video.VideoDecoderOutputBufferRenderer;
import com.mux.stats.sdk.core.CustomOptions;
import com.google.android.exoplayer2.video.VideoSize;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.core.model.CustomerViewData;
import com.mux.stats.sdk.core.util.MuxLogger;
import java.io.IOException;
import java.util.List;

public class MuxStatsExoPlayer extends MuxBaseExoPlayer implements AnalyticsListener,
    Player.Listener {

  static final String TAG = "MuxStatsEventQueue";

  @Deprecated
  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData) {
    this(ctx, player, playerName, customerPlayerData,
        customerVideoData, null, true);
  }

  @Deprecated
  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData,
      CustomerViewData customerViewData) {
    this(ctx, player, playerName, customerPlayerData, customerVideoData,
        customerViewData, true);
  }

  @Deprecated
  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData,
      boolean sentryEnabled) {
    this(ctx, player, playerName, customerPlayerData, customerVideoData,
        null, sentryEnabled);
  }

  @Deprecated
  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData,
      CustomerViewData customerViewData, boolean sentryEnabled) {
    this(ctx, player, playerName, new CustomerData(customerPlayerData, customerVideoData,
        customerViewData), sentryEnabled, new MuxNetworkRequests());
  }

  @Deprecated
  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData,
      CustomerViewData customerViewData, boolean sentryEnabled, INetworkRequest networkRequests) {
    this(ctx, player, playerName, new CustomerData(customerPlayerData, customerVideoData,
        customerViewData), sentryEnabled, networkRequests);
  }

  @Deprecated
  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerData data,
      boolean sentryEnabled,
      INetworkRequest networkRequests) {
    this(ctx, player, playerName, data, new CustomOptions().setSentryEnabled(sentryEnabled)
        , networkRequests);
  }

  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerData data) {
    this(ctx, player, playerName, data, new CustomOptions(), new MuxNetworkRequests());
  }

  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerData data,
      CustomOptions options,
      INetworkRequest networkRequests) {
    super(ctx, player, playerName, data, options, networkRequests);

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
    if (this.player != null && this.player.get() != null) {
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
      AudioAttributes audioAttributes) {
  }

  @Override
  public void onAudioUnderrun(AnalyticsListener.EventTime eventTime, int bufferSize,
      long bufferSizeMs, long elapsedSinceLastFeedMs) {
  }

  @Override
  public void onVideoInputFormatChanged(EventTime eventTime, Format format) {
    handleRenditionChange(format);
  }

  @Override
  public void onDownstreamFormatChanged(AnalyticsListener.EventTime eventTime,
      MediaLoadData mediaLoadData) {
    if (mediaLoadData.trackFormat != null
        && mediaLoadData.trackFormat.containerMimeType != null
        && detectMimeType) {
      mimeType = mediaLoadData.trackFormat.containerMimeType;
    }
  }

  @Override
  public void onDrmKeysLoaded(AnalyticsListener.EventTime eventTime) {
  }

  @Override
  public void onDrmKeysRemoved(AnalyticsListener.EventTime eventTime) {
  }

  @Override
  public void onDrmKeysRestored(AnalyticsListener.EventTime eventTime) {
  }

  @Override
  public void onDrmSessionManagerError(AnalyticsListener.EventTime eventTime, Exception e) {
    internalError(new MuxErrorException(ERROR_DRM, "DrmSessionManagerError - " + e.getMessage()));
  }

  // Note: onLoadingChanged was deprecated and moved to onIsLoadingChanged in 2.12.0
  @Override
  public void onIsLoadingChanged(AnalyticsListener.EventTime eventTime, boolean isLoading) {
  }

  @Override
  public void onIsPlayingChanged(AnalyticsListener.EventTime eventTime, boolean isPlaying) {
  }

  @Override
  public void onLoadCanceled(AnalyticsListener.EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    if (loadEventInfo.uri != null) {
      bandwidthDispatcher
          .onLoadCanceled(loadEventInfo.uri.getPath(), loadEventInfo.responseHeaders);
    } else {
      MuxLogger.d(TAG,
          "ERROR: onLoadCanceled called but mediaLoadData argument have no uri parameter.");
    }
  }

  @Override
  public void onLoadCompleted(AnalyticsListener.EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    if (loadEventInfo.uri != null) {
      bandwidthDispatcher.onLoadCompleted(loadEventInfo.uri.getPath(), loadEventInfo.bytesLoaded,
          mediaLoadData.trackFormat, loadEventInfo.responseHeaders);
    } else {
      MuxLogger.d(TAG,
          "ERROR: onLoadCompleted called but mediaLoadData argument have no uri parameter.");
    }
  }

  @Override
  public void onLoadError(AnalyticsListener.EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData, IOException e,
      boolean wasCanceled) {
    if (loadEventInfo.uri != null) {
      bandwidthDispatcher.onLoadError(loadEventInfo.uri.getPath(), e);
    } else {
      MuxLogger.d(TAG,
          "ERROR: onLoadError called but mediaLoadData argument have no uri parameter.");
    }
  }

  @Override
  public void onLoadStarted(AnalyticsListener.EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    if (loadEventInfo.uri != null) {
      String segmentMimeType = "unknown";
      if (mediaLoadData.trackFormat != null && mediaLoadData.trackFormat.sampleMimeType != null) {
        segmentMimeType = mediaLoadData.trackFormat.sampleMimeType;
      }
      bandwidthDispatcher
          .onLoadStarted(mediaLoadData.mediaStartTimeMs, mediaLoadData.mediaEndTimeMs,
              loadEventInfo.uri.getPath(), mediaLoadData.dataType,
              loadEventInfo.uri.getHost(), segmentMimeType);
    } else {
      MuxLogger.d(TAG,
          "ERROR: onLoadStarted called but mediaLoadData argument have no uri parameter.");
    }
  }

  @Override
  public void onMetadata(AnalyticsListener.EventTime eventTime, Metadata metadata) {
  }

  @Override
  public void onPlaybackParametersChanged(AnalyticsListener.EventTime eventTime,
      PlaybackParameters playbackParameters) {
    onPlaybackParametersChanged(playbackParameters);
  }

  @Override
  public void onPlaybackStateChanged(EventTime eventTime, @Player.State int state) {
    onPlaybackStateChanged(state);
  }

  @Override
  public void onPlaybackSuppressionReasonChanged(AnalyticsListener.EventTime eventTime,
      int playbackSuppressionReason) {
  }

  @Override
  public void onPlayerError(AnalyticsListener.EventTime eventTime, ExoPlaybackException error) {
    onPlayerError(error);
  }

  @Override
  public void onPlayWhenReadyChanged(AnalyticsListener.EventTime eventTime, boolean playWhenReady,
      int reason) {
    onPlayWhenReadyChanged(playWhenReady, reason);
    onPlaybackStateChanged(player.get().getPlaybackState());
  }

  @Override
  public void onPositionDiscontinuity(AnalyticsListener.EventTime eventTime, int reason) {
    onPositionDiscontinuity(reason);
  }

  @Override
  public void onRepeatModeChanged(AnalyticsListener.EventTime eventTime, int repeatMode) {
    onRepeatModeChanged(repeatMode);
  }

  // Note: onSeekProcessed was deprecated in 2.12.0

  @Override
  public void onSeekStarted(AnalyticsListener.EventTime eventTime) {
    seeking();
  }

  @Override
  public void onShuffleModeChanged(AnalyticsListener.EventTime eventTime,
      boolean shuffleModeEnabled) {
    onShuffleModeEnabledChanged(shuffleModeEnabled);
  }

  @Override
  public void onSurfaceSizeChanged(AnalyticsListener.EventTime eventTime, int width,
      int height) {
  }

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
  public void onUpstreamDiscarded(EventTime eventTime, MediaLoadData mediaLoadData) {
  }

  @Override
  public void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {
    sourceWidth = videoSize.width;
    sourceHeight = videoSize.height;
  }

  @Override
  public void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {
    firstFrameRenderedAt = System.currentTimeMillis();
    firstFrameReceived = true;
  }

  @Override
  public void onVolumeChanged(AnalyticsListener.EventTime eventTime, float volume) {
  }
  // ------END AnalyticsListener callbacks------

  // ------BEGIN Player.EventListener callbacks------
  @Override
  public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
  }

  @Override
  public void onPlaybackStateChanged(int playbackState) {
    /*
     * Sometimes onPlaybackStateChanged callback will not be triggered, and it is
     * prone to bugs to keep same value in two places, so we should always access
     * playWhenReady via the player object.
     */
    boolean playWhenReady = player.get().getPlayWhenReady();
    PlayerState state = this.getState();
    if (state == PlayerState.PLAYING_ADS) {
      // Ignore all normal events while playing ads
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
        if (state == PlayerState.PLAY || state == PlayerState.PLAYING) {
          // Player stop called !!!
          pause();
        }
      default:
        // don't care
        break;
    }
  }

  @Override
  public void onPlayerError(ExoPlaybackException e) {
    if (e.type == ExoPlaybackException.TYPE_RENDERER) {
      Exception cause = e.getRendererException();
      if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
        MediaCodecRenderer.DecoderInitializationException die = (MediaCodecRenderer.DecoderInitializationException) cause;
        if (die.codecInfo == null) {
          if (die.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
            internalError(new MuxErrorException(e.type, "Unable to query device decoders"));
          } else if (die.secureDecoderRequired) {
            internalError(new MuxErrorException(e.type, "No secure decoder for " + die.mimeType));
          } else {
            internalError(new MuxErrorException(e.type, "No decoder for " + die.mimeType));
          }
        } else {
          internalError(
              new MuxErrorException(e.type, "Unable to instantiate decoder for " + die.mimeType));
        }
      } else {
        internalError(new MuxErrorException(e.type,
            cause.getClass().getCanonicalName() + " - " + cause.getMessage()));
      }
    } else if (e.type == ExoPlaybackException.TYPE_SOURCE) {
      Exception error = e.getSourceException();
      internalError(new MuxErrorException(e.type,
          error.getClass().getCanonicalName() + " - " + error.getMessage()));
    } else if (e.type == ExoPlaybackException.TYPE_UNEXPECTED) {
      Exception error = e.getUnexpectedException();
      internalError(new MuxErrorException(e.type,
          error.getClass().getCanonicalName() + " - " + error.getMessage()));
    } else {
      internalError(e);
    }
  }

  @Override
  public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
    // Nothing to do here
  }

  @Override
  public void onPositionDiscontinuity(int reason) {
    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
      if (state == PlayerState.PAUSED || !playItemHaveVideoTrack) {
        seeked(false);
      }
    }
  }

  @Override
  public void onRepeatModeChanged(int repeatMode) {
  }

  // Note, onSeekProcessed was deprecated in 2.12.0

  @Override
  public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
  }

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
    configurePlaybackHeadUpdateInterval();
  }

  // Empty implementations of default methods from our interfaces
  // This is to workaround https://github.com/google/ExoPlayer/issues/6801

  @Override
  @Deprecated
  public void onPlayerStateChanged(
      EventTime eventTime, boolean playWhenReady, @Player.State int playbackState) {}

  @Override
  public void onMediaItemTransition(
      EventTime eventTime,
      @Nullable MediaItem mediaItem,
      @Player.MediaItemTransitionReason int reason) {}

  @Override
  public void onPositionDiscontinuity(
      EventTime eventTime,
      Player.PositionInfo oldPosition,
      Player.PositionInfo newPosition,
      @DiscontinuityReason int reason) {}

  @Override
  @Deprecated
  public void onSeekProcessed(EventTime eventTime) {}

  @Override
  @Deprecated
  public void onLoadingChanged(EventTime eventTime, boolean isLoading) {}

  @Override
  public void onStaticMetadataChanged(EventTime eventTime, List<Metadata> metadataList) {}

  @Override
  public void onMediaMetadataChanged(EventTime eventTime, MediaMetadata mediaMetadata) {}

  @Override
  public void onBandwidthEstimate(
      EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {}

  @Override
  @Deprecated
  public void onDecoderEnabled(
      EventTime eventTime, int trackType, DecoderCounters decoderCounters) {}

  @Override
  @Deprecated
  public void onDecoderInitialized(
      EventTime eventTime, int trackType, String decoderName, long initializationDurationMs) {}

  @Override
  @Deprecated
  public void onDecoderInputFormatChanged(EventTime eventTime, int trackType, Format format) {}

  @Override
  @Deprecated
  public void onDecoderDisabled(
      EventTime eventTime, int trackType, DecoderCounters decoderCounters) {}

  @Override
  public void onAudioEnabled(EventTime eventTime, DecoderCounters counters) {}

  @Override
  public void onAudioDecoderInitialized(
      EventTime eventTime,
      String decoderName,
      long initializedTimestampMs,
      long initializationDurationMs) {}

  @Override
  @Deprecated
  public void onAudioDecoderInitialized(
      EventTime eventTime, String decoderName, long initializationDurationMs) {}

  @Override
  @Deprecated
  public void onAudioInputFormatChanged(EventTime eventTime, Format format) {}

  @Override
  public void onAudioInputFormatChanged(
      EventTime eventTime,
      Format format,
      @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {}

  @Override
  public void onAudioPositionAdvancing(EventTime eventTime, long playoutStartSystemTimeMs) {}

  @Override
  public void onAudioDecoderReleased(EventTime eventTime, String decoderName) {}

  @Override
  public void onAudioDisabled(EventTime eventTime, DecoderCounters counters) {}

  @Override
  public void onAudioSessionIdChanged(EventTime eventTime, int audioSessionId) {}

  @Override
  public void onSkipSilenceEnabledChanged(EventTime eventTime, boolean skipSilenceEnabled) {}

  @Override
  public void onAudioSinkError(EventTime eventTime, Exception audioSinkError) {}

  @Override
  public void onAudioCodecError(EventTime eventTime, Exception audioCodecError) {}

  @Override
  public void onVideoEnabled(EventTime eventTime, DecoderCounters counters) {}

  @Override
  public void onVideoDecoderInitialized(
      EventTime eventTime,
      String decoderName,
      long initializedTimestampMs,
      long initializationDurationMs) {}

  @Override
  @Deprecated
  public void onVideoDecoderInitialized(
      EventTime eventTime, String decoderName, long initializationDurationMs) {}

  @Override
  public void onVideoInputFormatChanged(
      EventTime eventTime,
      Format format,
      @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {}

  @Override
  public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {}

  @Override
  public void onVideoDecoderReleased(EventTime eventTime, String decoderName) {}

  @Override
  public void onVideoDisabled(EventTime eventTime, DecoderCounters counters) {}

  @Override
  public void onVideoFrameProcessingOffset(
      EventTime eventTime, long totalProcessingOffsetUs, int frameCount) {}

  @Override
  public void onVideoCodecError(EventTime eventTime, Exception videoCodecError) {}

  @Override
  @Deprecated
  public void onVideoSizeChanged(
      EventTime eventTime,
      int width,
      int height,
      int unappliedRotationDegrees,
      float pixelWidthHeightRatio) {}

  @Override
  @Deprecated
  public void onDrmSessionAcquired(EventTime eventTime) {}

  @Override
  public void onDrmSessionAcquired(EventTime eventTime, @DrmSession.State int state) {}

  @Override
  public void onDrmSessionReleased(EventTime eventTime) {}

  @Override
  public void onPlayerReleased(EventTime eventTime) {}

  @Override
  public void onEvents(Player player, Events events) {}
}
