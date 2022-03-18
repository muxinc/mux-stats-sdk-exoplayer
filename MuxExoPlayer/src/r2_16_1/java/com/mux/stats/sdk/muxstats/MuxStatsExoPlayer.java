package com.mux.stats.sdk.muxstats;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
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
import com.google.android.exoplayer2.source.hls.HlsManifest;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
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
      @Deprecated boolean unused) {
    this(ctx, player, playerName, customerPlayerData, customerVideoData,
        null, unused);
  }

  @Deprecated
  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData,
      CustomerViewData customerViewData, @Deprecated boolean unused) {
    this(ctx, player, playerName, new CustomerData(customerPlayerData, customerVideoData,
        customerViewData), unused, new MuxNetworkRequests());
  }

  @Deprecated
  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerPlayerData customerPlayerData,
      CustomerVideoData customerVideoData,
      CustomerViewData customerViewData, @Deprecated boolean unused, INetworkRequest networkRequests) {
    this(ctx, player, playerName, new CustomerData(customerPlayerData, customerVideoData,
        customerViewData), unused, networkRequests);
  }

  @Deprecated
  public MuxStatsExoPlayer(Context ctx, ExoPlayer player, String playerName,
      CustomerData data,
      @Deprecated boolean unused,
      INetworkRequest networkRequests) {
    this(ctx, player, playerName, data, new CustomOptions()
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

  /**
   * Extracts the tag value from live HLS segment, returns -1 if it is not an HLS stream, not a live
   * playback.
   *
   * @param tagName name of the tag to extract from the HLS manifest.
   * @return tag value if tag is found and we are playing HLS live stream, -1 string otherwise.
   */
  @Override
  protected String parseHlsManifestTag(String tagName) {
    Log.d("TAGTAG", "parsing hls manifest tag: " + tagName);
    synchronized (currentTimelineWindow) {
      Log.d("TAGTAG", "Current timeline window is " + currentTimelineWindow);
      if (currentTimelineWindow != null && currentTimelineWindow.manifest != null
          && tagName != null && tagName.length() > 0) {
        if (currentTimelineWindow.manifest instanceof HlsManifest) {
          HlsManifest manifest = (HlsManifest) currentTimelineWindow.manifest;
          Log.d("TAGTAG", "playlist tags " + manifest.mediaPlaylist.tags);
          if (manifest.mediaPlaylist.tags != null) {
            for (String tag : manifest.mediaPlaylist.tags) {
              if (tag.contains(tagName)) {
                String value = tag.split(tagName)[1];
                if (value.contains(",")) {
                  value = value.split(",")[0];
                }
                if (value.startsWith("=") || value.startsWith(":")) {
                  value = value.substring(1, value.length());
                }
                Log.i("TAGTAG", "Parsed tag value " + value);
                return value;
              }
            }
          }
        }
      }
    }

    Log.i("TAGTAG", "No tag with that value");
    return "-1";
  }

  @Override
  protected boolean isLivePlayback() {
    if (currentTimelineWindow != null) {
      return currentTimelineWindow.isLive();
    }
    return false;
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
  public void onPlayerError(AnalyticsListener.EventTime eventTime, PlaybackException error) {
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
  public void onPlayerError(@NonNull PlaybackException e) {
    if (e instanceof ExoPlaybackException) {
      handleExoPlaybackException((ExoPlaybackException) e);
    } else {
      int errorCode = ExoPlaybackException.TYPE_UNEXPECTED;
      String errorMessage = e.getErrorCodeName() + ": " + e.getMessage();
      switch (e.errorCode) {
        case PlaybackException.ERROR_CODE_REMOTE_ERROR:
          errorCode = ExoPlaybackException.TYPE_REMOTE;
          break;
        case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
        case PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED: //fall-through
        case PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND: //fall-through
        case PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE: //fall-through
        case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: //fall-through
        case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT: //fall-through
        case PlaybackException.ERROR_CODE_IO_NO_PERMISSION: //fall-through
        case PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE: //fall-through
        case PlaybackException.ERROR_CODE_IO_UNSPECIFIED: //fall-through
        case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED: //fall-through
        case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED: //fall-through
        case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED: //fall-through
        case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED: //fall-through
        case PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR: //fall-through
        case PlaybackException.ERROR_CODE_DRM_UNSPECIFIED: //fall-through
        case PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED: //fall-through
        case PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED: //fall-through
        case PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED: //fall-through
        case PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION: //fall-through
        case PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR: //fall-through
        case PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED: //fall-through
        case PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED:
          errorCode = ExoPlaybackException.TYPE_SOURCE;
          break;
        case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:  //fall-through
        case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED: //fall-through
        case PlaybackException.ERROR_CODE_DECODING_FAILED: //fall-through
        case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED: //fall-through
        case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES: //fall-through
        case PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED: //fall-through
        case PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED:
          errorCode = ExoPlaybackException.TYPE_RENDERER;
        default:
          break;
      }

      internalError(new MuxErrorException(errorCode, errorMessage));
    }
  }

  private void handleExoPlaybackException(ExoPlaybackException e) {
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
    Log.d("sessiondata2", "onTimelineChanged(): called");
    ExoPlayer exoPlayer = player.get();
    if(exoPlayer != null) {
      HlsManifest manifest = Util.safeCast(exoPlayer.getCurrentManifest(), HlsManifest.class);
      Log.v("sessiondata2", "onTimelineChanged(): manifest is " + manifest);
      if(manifest != null) {
        List<String> tags = manifest.masterPlaylist.tags;
        for (String tag : tags) {
          Log.d("sessiondata2", "tag " + tag);
        }
      }
    }

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
