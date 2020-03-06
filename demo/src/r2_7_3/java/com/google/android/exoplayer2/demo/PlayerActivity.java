/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.demo;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.ContentType;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;
import com.mux.stats.sdk.core.MuxSDKViewOrientation;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.muxstats.MuxStatsExoPlayer;

import java.lang.reflect.Constructor;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.UUID;

/** An activity that plays media using {@link SimpleExoPlayer}. */
public class PlayerActivity extends Activity
    implements OnClickListener, PlaybackPreparer, PlayerControlView.VisibilityListener {

  public static final String DRM_SCHEME_EXTRA = "drm_scheme";
  public static final String DRM_LICENSE_URL = "drm_license_url";
  public static final String DRM_KEY_REQUEST_PROPERTIES = "drm_key_request_properties";
  public static final String DRM_MULTI_SESSION = "drm_multi_session";
  public static final String PREFER_EXTENSION_DECODERS = "prefer_extension_decoders";

  public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
  public static final String EXTENSION_EXTRA = "extension";

  public static final String ACTION_VIEW_LIST =
      "com.google.android.exoplayer.demo.action.VIEW_LIST";
  public static final String URI_LIST_EXTRA = "uri_list";
  public static final String EXTENSION_LIST_EXTRA = "extension_list";
  public static final String AD_TAG_URI_EXTRA = "ad_tag_uri";
  public static final String VIDEO_TITLE_EXTRA = "video_title";

  // For backwards compatibility.
  private static final String DRM_SCHEME_UUID_EXTRA = "drm_scheme_uuid";

  private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
  private static final CookieManager DEFAULT_COOKIE_MANAGER;
  static {
    DEFAULT_COOKIE_MANAGER = new CookieManager();
    DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  private Handler mainHandler;
  private EventLogger eventLogger;
  private PlayerView playerView;
  private LinearLayout debugRootView;
  private TextView debugTextView;

  private DataSource.Factory mediaDataSourceFactory;
  private SimpleExoPlayer player;
  private DefaultTrackSelector trackSelector;
  private TrackSelectionHelper trackSelectionHelper;
  private DebugTextViewHelper debugViewHelper;
  private boolean inErrorState;
  private TrackGroupArray lastSeenTrackGroupArray;

  private boolean shouldAutoPlay;
  private int resumeWindow;
  private long resumePosition;

  // Fields used only for ad playback. The ads loader is loaded via reflection.

  private AdsLoader adsLoader;
  private Uri loadedAdTagUri;
  private ViewGroup adUiViewGroup;

  private MuxStatsExoPlayer muxStats;

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    shouldAutoPlay = true;
    clearResumePosition();
    mediaDataSourceFactory = buildDataSourceFactory(true);
    mainHandler = new Handler();
    if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
      CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
    }

    setContentView(R.layout.player_activity);
    View rootView = findViewById(R.id.root);
    rootView.setOnClickListener(this);
    debugRootView = (LinearLayout)findViewById(R.id.controls_root);
    debugTextView = (TextView)findViewById(R.id.debug_text_view);

    playerView = (PlayerView)findViewById(R.id.player_view);
    playerView.setControllerVisibilityListener(this);
    playerView.requestFocus();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    if (muxStats == null) {
      return;
    }
    if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      muxStats.orientationChange(MuxSDKViewOrientation.LANDSCAPE);
    }
    if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
      muxStats.orientationChange(MuxSDKViewOrientation.PORTRAIT);
    }
  }

  @Override
  public void onNewIntent(Intent intent) {
    releasePlayer();
    shouldAutoPlay = true;
    clearResumePosition();
    setIntent(intent);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (Util.SDK_INT > 23) {
      initializePlayer();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (Util.SDK_INT <= 23 || player == null) {
      initializePlayer();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      releasePlayer();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      releasePlayer();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    releaseAdsLoader();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      initializePlayer();
    } else {
      showToast(R.string.storage_permission_denied);
      finish();
    }
  }

  // Activity input

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // See whether the player view wants to handle media or DPAD keys events.
    return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
  }

  // OnClickListener methods

  @Override
  public void onClick(View view) {
    if (view.getParent() == debugRootView) {
      MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
      if (mappedTrackInfo != null) {
        trackSelectionHelper.showSelectionDialog(
            this, ((Button) view).getText(), mappedTrackInfo, (int) view.getTag());
      }
    }
  }

  // PlaybackControlView.PlaybackPreparer implementation

  @Override
  public void preparePlayback() {
    initializePlayer();
  }

  // PlaybackControlView.VisibilityListener implementation

  @Override
  public void onVisibilityChange(int visibility) {
    debugRootView.setVisibility(visibility);
  }

  // Internal methods

  private void initializePlayer() {
    Intent intent = getIntent();
    boolean needNewPlayer = player == null;
    if (needNewPlayer) {
      TrackSelection.Factory adaptiveTrackSelectionFactory =
          new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
      trackSelector = new DefaultTrackSelector(adaptiveTrackSelectionFactory);
      trackSelectionHelper = new TrackSelectionHelper(trackSelector, adaptiveTrackSelectionFactory);
      lastSeenTrackGroupArray = null;
      eventLogger = new EventLogger(trackSelector);

      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
      if (intent.hasExtra(DRM_SCHEME_EXTRA) || intent.hasExtra(DRM_SCHEME_UUID_EXTRA)) {
        String drmLicenseUrl = intent.getStringExtra(DRM_LICENSE_URL);
        String[] keyRequestPropertiesArray = intent.getStringArrayExtra(DRM_KEY_REQUEST_PROPERTIES);
        boolean multiSession = intent.getBooleanExtra(DRM_MULTI_SESSION, false);
        int errorStringId = R.string.error_drm_unknown;
        if (Util.SDK_INT < 18) {
          errorStringId = R.string.error_drm_not_supported;
        } else {
          try {
            String drmSchemeExtra = intent.hasExtra(DRM_SCHEME_EXTRA) ? DRM_SCHEME_EXTRA
                : DRM_SCHEME_UUID_EXTRA;
            UUID drmSchemeUuid = Util.getDrmUuid(intent.getStringExtra(drmSchemeExtra));
            if (drmSchemeUuid == null) {
              errorStringId = R.string.error_drm_unsupported_scheme;
            } else {
              drmSessionManager =
                  buildDrmSessionManagerV18(
                      drmSchemeUuid, drmLicenseUrl, keyRequestPropertiesArray, multiSession);
            }
          } catch (UnsupportedDrmException e) {
            errorStringId = e.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown;
          }
        }
        if (drmSessionManager == null) {
          showToast(errorStringId);
          return;
        }
      }

      boolean preferExtensionDecoders = intent.getBooleanExtra(PREFER_EXTENSION_DECODERS, false);
      @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode =
          ((DemoApplication) getApplication()).useExtensionRenderers()
              ? (preferExtensionDecoders ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
              : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
              : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
      DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this,
          drmSessionManager, extensionRendererMode);

      player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector);
      player.addListener(new PlayerEventListener());
      CustomerPlayerData customerPlayerData = new CustomerPlayerData();
      customerPlayerData.setEnvironmentKey("YOUR_ENVIRONMENT_KEY");
      CustomerVideoData customerVideoData = new CustomerVideoData();
      customerVideoData.setVideoTitle(intent.getStringExtra(VIDEO_TITLE_EXTRA));
      muxStats = new MuxStatsExoPlayer(this, player, "demo-player", customerPlayerData, customerVideoData);
      Point size = new Point();
      getWindowManager().getDefaultDisplay().getSize(size);
      muxStats.setScreenSize(size.x, size.y);
      muxStats.setPlayerView(playerView);
      muxStats.enableMuxCoreDebug(true, false);

      player.addListener(eventLogger);
      player.addMetadataOutput(muxStats.getMetadataRendererOutput(eventLogger));
      player.addAudioDebugListener(muxStats.getAudioRendererEventListener(eventLogger));
      player.addVideoDebugListener(muxStats.getVideoRendererEventListener(eventLogger));
      player.setPlayWhenReady(shouldAutoPlay);

      playerView.setPlayer(player);
      playerView.setPlaybackPreparer(this);
      debugViewHelper = new DebugTextViewHelper(player, debugTextView);
      debugViewHelper.start();
    }
    String action = intent.getAction();
    Uri[] uris;
    String[] extensions;
    if (ACTION_VIEW.equals(action)) {
      uris = new Uri[]{intent.getData()};
      extensions = new String[]{intent.getStringExtra(EXTENSION_EXTRA)};
    } else if (ACTION_VIEW_LIST.equals(action)) {
      String[] uriStrings = intent.getStringArrayExtra(URI_LIST_EXTRA);
      uris = new Uri[uriStrings.length];
      for (int i = 0; i < uriStrings.length; i++) {
        uris[i] = Uri.parse(uriStrings[i]);
      }
      extensions = intent.getStringArrayExtra(EXTENSION_LIST_EXTRA);
      if (extensions == null) {
        extensions = new String[uriStrings.length];
      }
    } else {
      showToast(getString(R.string.unexpected_intent_action, action));
      return;
    }
    if (Util.maybeRequestReadExternalStoragePermission(this, uris)) {
      // The player will be reinitialized if the permission is granted.
      return;
    }
    MediaSource[] mediaSources = new MediaSource[uris.length];
    for (int i = 0; i < uris.length; i++) {
      mediaSources[i] = buildMediaSource(uris[i], extensions[i], mainHandler, eventLogger);
    }
    MediaSource mediaSource = mediaSources.length == 1 ? mediaSources[0]
        : new ConcatenatingMediaSource(mediaSources);
    String adTagUriString = intent.getStringExtra(AD_TAG_URI_EXTRA);
    if (adTagUriString != null) {
      Uri adTagUri = Uri.parse(adTagUriString);
      if (!adTagUri.equals(loadedAdTagUri)) {
        releaseAdsLoader();
        loadedAdTagUri = adTagUri;
      }
      MediaSource adsMediaSource = createAdsMediaSource(mediaSource, Uri.parse(adTagUriString));
      if (adsMediaSource != null) {
        mediaSource = adsMediaSource;
      } else {
      }
    } else {
      releaseAdsLoader();
    }
    boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
    if (haveResumePosition) {
      player.seekTo(resumeWindow, resumePosition);
    }
    player.prepare(mediaSource, !haveResumePosition, false);
    inErrorState = false;
    updateButtonVisibilities();
  }

  private MediaSource buildMediaSource(
      Uri uri,
      String overrideExtension,
      @Nullable Handler handler,
      @Nullable MediaSourceEventListener listener) {
    @ContentType int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri)
        : Util.inferContentType("." + overrideExtension);
    switch (type) {
      case C.TYPE_DASH:
        return new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(mediaDataSourceFactory),
                buildDataSourceFactory(false))
            .createMediaSource(uri, handler, muxStats.getMediaSourceEventListener(type, eventLogger));
      case C.TYPE_SS:
        return new SsMediaSource.Factory(
                new DefaultSsChunkSource.Factory(mediaDataSourceFactory),
                buildDataSourceFactory(false))
            .createMediaSource(uri, handler, muxStats.getMediaSourceEventListener(type, eventLogger));
      case C.TYPE_HLS:
        return new HlsMediaSource.Factory(mediaDataSourceFactory)
            .createMediaSource(uri, handler, muxStats.getMediaSourceEventListener(type, eventLogger));
      case C.TYPE_OTHER:
        return new ExtractorMediaSource.Factory(mediaDataSourceFactory)
            .createMediaSource(uri, handler, muxStats.getMediaSourceEventListener(type, eventLogger));
      default: {
        throw new IllegalStateException("Unsupported type: " + type);
      }
    }
  }

  private DrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManagerV18(UUID uuid,
      String licenseUrl, String[] keyRequestPropertiesArray, boolean multiSession)
      throws UnsupportedDrmException {
    HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl,
        buildHttpDataSourceFactory(false));
    if (keyRequestPropertiesArray != null) {
      for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
        drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i],
            keyRequestPropertiesArray[i + 1]);
      }
    }
    return new DefaultDrmSessionManager<>(uuid, FrameworkMediaDrm.newInstance(uuid), drmCallback,
        null, mainHandler, muxStats.getStreamingDrmSessionManagerEventListener(eventLogger), multiSession);
  }

  private void releasePlayer() {
    if (player != null) {
      debugViewHelper.stop();
      debugViewHelper = null;
      shouldAutoPlay = player.getPlayWhenReady();
      updateResumePosition();
      muxStats.release();
      player.release();
      player = null;
      trackSelector = null;
      trackSelectionHelper = null;
      eventLogger = null;
    }
  }

  private void updateResumePosition() {
    resumeWindow = player.getCurrentWindowIndex();
    resumePosition = Math.max(0, player.getContentPosition());
  }

  private void clearResumePosition() {
    resumeWindow = C.INDEX_UNSET;
    resumePosition = C.TIME_UNSET;
  }

  /**
   * Returns a new DataSource factory.
   *
   * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
   *     DataSource factory.
   * @return A new DataSource factory.
   */
  private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
    return ((DemoApplication) getApplication())
        .buildDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
  }

  /**
   * Returns a new HttpDataSource factory.
   *
   * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
   *     DataSource factory.
   * @return A new HttpDataSource factory.
   */
  private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter) {
    return ((DemoApplication) getApplication())
        .buildHttpDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
  }

  /** Returns an ads media source, reusing the ads loader if one exists. */
  private @Nullable MediaSource createAdsMediaSource(MediaSource mediaSource, Uri adTagUri) {
    // Load the extension source using reflection so the demo app doesn't have to depend on it.
    // The ads loader is reused for multiple playbacks, so that ad playback can resume.
    try {
      Class<?> loaderClass = Class.forName("com.google.android.exoplayer2.ext.ima.ImaAdsLoader");
      if (adsLoader == null) {
        // Full class names used so the LINT.IfChange rule triggers should any of the classes move.
        // LINT.IfChange
        Constructor<? extends AdsLoader> loaderConstructor =
            loaderClass
                .asSubclass(AdsLoader.class)
                .getConstructor(android.content.Context.class, android.net.Uri.class);
        // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
        adsLoader = loaderConstructor.newInstance(this, adTagUri);
        adUiViewGroup = new FrameLayout(this);
        // The demo app has a non-null overlay frame layout.
        playerView.getOverlayFrameLayout().addView(adUiViewGroup);
      }
      AdsMediaSource.MediaSourceFactory adMediaSourceFactory =
          new AdsMediaSource.MediaSourceFactory() {
            @Override
            public MediaSource createMediaSource(
                Uri uri, @Nullable Handler handler, @Nullable MediaSourceEventListener listener) {
              return PlayerActivity.this.buildMediaSource(
                  uri, /* overrideExtension= */ null, handler, listener);
            }

            @Override
            public int[] getSupportedTypes() {
              return new int[] {C.TYPE_DASH, C.TYPE_SS, C.TYPE_HLS, C.TYPE_OTHER};
            }
          };
      return new AdsMediaSource(
          mediaSource, adMediaSourceFactory, adsLoader, adUiViewGroup, mainHandler, eventLogger);
    } catch (ClassNotFoundException e) {
      // IMA extension not loaded.
      return null;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void releaseAdsLoader() {
    if (adsLoader != null) {
      adsLoader.release();
      adsLoader = null;
      loadedAdTagUri = null;
      playerView.getOverlayFrameLayout().removeAllViews();
    }
  }

  // User controls

  private void updateButtonVisibilities() {
    debugRootView.removeAllViews();
    if (player == null) {
      return;
    }

    MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
    if (mappedTrackInfo == null) {
      return;
    }

    for (int i = 0; i < mappedTrackInfo.length; i++) {
      TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(i);
      if (trackGroups.length != 0) {
        Button button = new Button(this);
        int label;
        switch (player.getRendererType(i)) {
          case C.TRACK_TYPE_AUDIO:
            label = R.string.audio;
            break;
          case C.TRACK_TYPE_VIDEO:
            label = R.string.video;
            break;
          case C.TRACK_TYPE_TEXT:
            label = R.string.text;
            break;
          default:
            continue;
        }
        button.setText(label);
        button.setTag(i);
        button.setOnClickListener(this);
        debugRootView.addView(button);
      }
    }
  }

  private void showControls() {
    debugRootView.setVisibility(View.VISIBLE);
  }

  private void showToast(int messageId) {
    showToast(getString(messageId));
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

  private static boolean isBehindLiveWindow(ExoPlaybackException e) {
    if (e.type != ExoPlaybackException.TYPE_SOURCE) {
      return false;
    }
    Throwable cause = e.getSourceException();
    while (cause != null) {
      if (cause instanceof BehindLiveWindowException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private class PlayerEventListener extends Player.DefaultEventListener {

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      if (playbackState == Player.STATE_ENDED) {
        showControls();
      }
      updateButtonVisibilities();
    }

    @Override
    public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
      if (inErrorState) {
        // This will only occur if the user has performed a seek whilst in the error state. Update
        // the resume position so that if the user then retries, playback will resume from the
        // position to which they seeked.
        updateResumePosition();
      }
    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
      String errorString = null;
      if (e.type == ExoPlaybackException.TYPE_RENDERER) {
        Exception cause = e.getRendererException();
        if (cause instanceof DecoderInitializationException) {
          // Special case for decoder initialization failures.
          DecoderInitializationException decoderInitializationException =
              (DecoderInitializationException) cause;
          if (decoderInitializationException.decoderName == null) {
            if (decoderInitializationException.getCause() instanceof DecoderQueryException) {
              errorString = getString(R.string.error_querying_decoders);
            } else if (decoderInitializationException.secureDecoderRequired) {
              errorString = getString(R.string.error_no_secure_decoder,
                  decoderInitializationException.mimeType);
            } else {
              errorString = getString(R.string.error_no_decoder,
                  decoderInitializationException.mimeType);
            }
          } else {
            errorString = getString(R.string.error_instantiating_decoder,
                decoderInitializationException.decoderName);
          }
        }
      }
      if (errorString != null) {
        showToast(errorString);
      }
      inErrorState = true;
      if (isBehindLiveWindow(e)) {
        clearResumePosition();
        initializePlayer();
      } else {
        updateResumePosition();
        updateButtonVisibilities();
        showControls();
      }
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
      updateButtonVisibilities();
      if (trackGroups != lastSeenTrackGroupArray) {
        MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
          if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_VIDEO)
              == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
            showToast(R.string.error_unsupported_video);
          }
          if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_AUDIO)
              == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
            showToast(R.string.error_unsupported_audio);
          }
        }
        lastSeenTrackGroupArray = trackGroups;
      }
    }

  }

}
