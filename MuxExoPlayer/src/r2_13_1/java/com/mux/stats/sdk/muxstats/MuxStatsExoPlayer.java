package com.mux.stats.sdk.muxstats;

import android.content.Context;
import android.util.Log;
import android.view.Surface;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.core.model.CustomerViewData;
import java.io.IOException;

public class MuxStatsExoPlayer extends MuxBaseExoPlayer implements AnalyticsListener,
    Player.EventListener {

  static final String TAG = "MuxStatsEventQueue";

  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData) {
    this(ctx, player, playerName, customerPlayerData,
        customerVideoData, null, true);
  }

  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData,
      CustomerViewData customerViewData) {
    this(ctx, player, playerName, customerPlayerData, customerVideoData,
        customerViewData, true);
  }

  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData,
      boolean sentryEnabled) {
    this(ctx, player, playerName, customerPlayerData, customerVideoData,
        null, sentryEnabled);
  }

  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData,
      CustomerViewData customerViewData, boolean sentryEnabled) {
    this(ctx, player, playerName, customerPlayerData, customerVideoData,
        customerViewData, sentryEnabled, new MuxNetworkRequests());
  }

  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData,
      CustomerViewData customerViewData, boolean sentryEnabled,
      INetworkRequest networkRequests) {
    super(ctx, player, playerName, customerPlayerData, customerVideoData,
        customerViewData, sentryEnabled, networkRequests);

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
    detectStreamType(format);
    handleRenditionChange(format);
  }

  @Override
  public void onDownstreamFormatChanged(AnalyticsListener.EventTime eventTime,
      MediaLoadData mediaLoadData) {
    if (mediaLoadData.trackFormat != null && mediaLoadData.trackFormat.containerMimeType != null) {
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
    bandwidthDispatcher.onLoadCanceled(loadEventInfo.uri.getPath(), loadEventInfo.responseHeaders);
  }

  @Override
  public void onLoadCompleted(AnalyticsListener.EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    bandwidthDispatcher.onLoadCompleted(loadEventInfo.uri.getPath(), loadEventInfo.bytesLoaded,
        mediaLoadData.trackFormat, loadEventInfo.responseHeaders);
  }

  @Override
  public void onLoadError(AnalyticsListener.EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData, IOException e,
      boolean wasCanceled) {
    bandwidthDispatcher.onLoadError(loadEventInfo.uri.getPath(), e);
  }

  @Override
  public void onLoadStarted(AnalyticsListener.EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {
    bandwidthDispatcher.onLoadStarted(mediaLoadData.mediaStartTimeMs, mediaLoadData.mediaEndTimeMs,
        loadEventInfo.uri.getPath(), mediaLoadData.dataType, loadEventInfo.uri.getHost());
  }

  @Override
  public void onMetadata(AnalyticsListener.EventTime eventTime, Metadata metadata) {
    Log.i("MuxStats", "Got metadata !!!");
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
  public void onRenderedFirstFrame(AnalyticsListener.EventTime eventTime, Surface surface) {
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
  public void onVideoSizeChanged(AnalyticsListener.EventTime eventTime, int width, int height,
      int unappliedRotationDegrees, float pixelWidthHeightRatio) {
    sourceWidth = width;
    sourceHeight = height;
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
}
