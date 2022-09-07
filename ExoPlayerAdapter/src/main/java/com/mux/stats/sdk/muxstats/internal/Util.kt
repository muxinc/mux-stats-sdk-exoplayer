package com.mux.stats.sdk.muxstats.internal

import android.util.Log
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.hls.HlsManifest
import com.mux.stats.sdk.core.util.MuxLogger
import com.mux.stats.sdk.muxstats.MuxErrorException
import com.mux.stats.sdk.muxstats.MuxPlayerState
import com.mux.stats.sdk.muxstats.MuxStateCollector
import com.mux.stats.sdk.muxstats.MuxStateCollectorBase

// -- General Utils --

/**
 * Returns true if the object is one of any of the parameters supplied
 */
@JvmSynthetic // Hide from Java callers because all are external
internal fun Any.oneOf(vararg accept: Any) = accept.contains(this)

/**
 * Returns true if the object is not any of the parameters supplied
 */
@JvmSynthetic // Hide from Java callers because all are external
internal fun Any.noneOf(vararg accept: Any) = !accept.contains(this)

/**
 * Gets a Log Tag from the name of the calling class. Can be used in any package that isn't
 * obfuscated (such as muxstats)
 */
@Suppress("unused")
internal inline fun <reified T> T.logTag() = T::class.java.simpleName

// -- ExoPlayer Helpers --

// lazily-cached check for the HLS extension, which may not be available at runtime
private val hlsExtensionAvailable: Boolean by lazy {
  try {
    Class.forName(HlsManifest::class.java.canonicalName!!)
    true
  } catch (e: ClassNotFoundException) {
    MuxLogger.w("isHlsExtensionAvailable", "HLS extension not found. Some features may not work")
    false
  }
}

@JvmSynthetic
internal fun isHlsExtensionAvailable() = hlsExtensionAvailable

/**
 * Handles an ExoPlayer position discontinuity
 */
@JvmSynthetic // Hides from java
internal fun MuxStateCollector.handlePositionDiscontinuity(reason: Int) {
  when (reason) {
    Player.DISCONTINUITY_REASON_SEEK -> {
      // If they seek while paused, this is how we know the seek is complete
      if (muxPlayerState == MuxPlayerState.PAUSED
        // Seeks on audio-only media are reported this way instead
        || !mediaHasVideoTrack!!
      ) {
        seeked(false)
      }
    }
    else -> {} // ignored
  }
}

//fun MuxPlayerAdapter<View, ExoPlayer, ExoPlayer>.allowHeaderToBeSentToBackend(headerName: String?) {
//  basicMetrics.
//}

/**
 * Handles a change of basic ExoPlayer state
 */
@JvmSynthetic // Hidden from Java callers, since the only ones are external
internal fun MuxStateCollector.handleExoPlaybackState(
  playbackState: Int, // the @IntDef for player state omitted. Unavailable on all exo versions
  playWhenReady: Boolean
) {
  Log.v("playstate", "player state changed: State is $playbackState and PWR is $playWhenReady");
  if (this.muxPlayerState == MuxPlayerState.PLAYING_ADS) {
    // Normal playback events are ignored during ad playback
    return
  }

  when (playbackState) {
    Player.STATE_BUFFERING -> {
      buffering()
      if (playWhenReady) {
        play()
      } else if (muxPlayerState != MuxPlayerState.PAUSED) {
        pause()
      }
    }
    Player.STATE_READY -> {
      if (playWhenReady) {
        Log.e("MuxStats", "Playing called from handleExoPlaybackState event !!!")
        playing()
      } else if (muxPlayerState != MuxPlayerState.PAUSED) {
        pause()
      }
    }
    Player.STATE_ENDED -> {
      ended()
    }
    Player.STATE_IDLE -> {
      if (muxPlayerState.oneOf(MuxPlayerState.PLAY, MuxPlayerState.PLAYING)) {
        // If we are playing/preparing to play and go idle, the player was stopped
        pause()
      }
    }
  } // when (playbackState)
} // fun handleExoPlaybackState

@JvmSynthetic
internal fun MuxStateCollector.handleExoPlaybackException(e: ExoPlaybackException) {
  if (e.type == ExoPlaybackException.TYPE_RENDERER) {
    val cause = e.rendererException
    if (cause is MediaCodecRenderer.DecoderInitializationException) {
      val die = cause
        // TODO split this by versions and implement else block to ghet the codec details
//      if (die.codecInfo == null) {
        if (die.cause is MediaCodecUtil.DecoderQueryException) {
          internalError(MuxErrorException(e.type, "Unable to query device decoders"))
        } else if (die.secureDecoderRequired) {
          internalError(MuxErrorException(e.type, "No secure decoder for " + die.mimeType))
        } else {
          internalError(MuxErrorException(e.type, "No decoder for " + die.mimeType))
        }
//      }
//    else {
//        internalError(
//          MuxErrorException(e.type, "Unable to instantiate decoder for " + die.mimeType)
//        )
//      } // if(die.codecInfo)..else
    } else {
      internalError(
        MuxErrorException(
          e.type,
          "${cause.javaClass.canonicalName} - ${cause.message}"
        )
      )
    } // if(cause is..)...else
  } else if (e.type == ExoPlaybackException.TYPE_SOURCE) {
    val error: Exception = e.sourceException
    internalError(
      MuxErrorException(
        e.type,
        "${error.javaClass.canonicalName} - ${error.message}"
      )
    )
  } else if (e.type == ExoPlaybackException.TYPE_UNEXPECTED) {
    val error: Exception = e.unexpectedException
    internalError(
      MuxErrorException(
        e.type,
        "${error.javaClass.canonicalName} - ${error.message}"
      )
    )
  } else {
    internalError(e)
  }
}

/////////////////////////////////////////////////////////////
/// ExoPlayer Helper Functions //////////////////////////////

fun ExoPlayer.MuxMediaHasVideoTrack(): Boolean {
  val trackGroups:TrackGroupArray = getCurrentTrackGroups();
  var playItemHaveVideoTrack = false;
  if (trackGroups.length > 0) {
    for (groupIndex in 0 until trackGroups.length-1) {
      val trackGroup:TrackGroup = trackGroups.get(groupIndex);
      if (0 < trackGroup.length) {
        val trackFormat:Format = trackGroup.getFormat(0);
        if (trackFormat.sampleMimeType != null && trackFormat.sampleMimeType!!.contains("video")) {
          playItemHaveVideoTrack = true;
          break;
        }
      }
    }
  }
  return playItemHaveVideoTrack;
}

/**
 * Returns and starts an object that will poll ExoPlayer for its content position every so often
 * and updated the given MuxStateCollector
 */
@Suppress("unused") // this method is used with some versions of ExoPlayer
@JvmSynthetic // Hidden from Java callers, since the only ones are external
internal fun ExoPlayer.watchContentPosition(stateCollector: MuxStateCollector):
        MuxStateCollectorBase.PositionWatcher =
  ExoPositionWatcher(this, stateCollector).apply { start() }

// -- private helper classes

/**
 * Watches an ExoPlayer's position, polling it every {@link #UPDATE_INTERVAL_MILIS} milliseconds
 */
private class ExoPositionWatcher(player: ExoPlayer, stateCollector: MuxStateCollector) :
  MuxStateCollectorBase.PositionWatcher(
    UPDATE_INTERVAL_MILLIS, stateCollector
  ) {
  companion object {
    const val UPDATE_INTERVAL_MILLIS = 150L
  }

  private val player by weak(player) // don't hold the player because this object does async looping
  override fun getTimeMillis(): Long? = player?.contentPosition
}
