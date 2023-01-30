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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.TracksInfo;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader;
import com.google.android.exoplayer2.ext.ima.ImaServerSideAdInsertionMediaSource;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.StyledPlayerControlView;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.DebugTextViewHelper;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;
import com.mux.stats.sdk.core.MuxSDKViewOrientation;
import com.mux.stats.sdk.core.model.CustomData;
import com.mux.stats.sdk.core.model.CustomerData;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.muxstats.MuxStatsExoPlayer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** An activity that plays media using {@link ExoPlayer}. */
public class PlayerActivity extends AppCompatActivity
    implements OnClickListener, StyledPlayerControlView.VisibilityListener {

  // Saved instance state keys.

  private static final String KEY_TRACK_SELECTION_PARAMETERS = "track_selection_parameters";
  private static final String KEY_SERVER_SIDE_ADS_LOADER_STATE = "server_side_ads_loader_state";
  private static final String KEY_ITEM_INDEX = "item_index";
  private static final String KEY_POSITION = "position";
  private static final String KEY_AUTO_PLAY = "auto_play";

  protected StyledPlayerView playerView;
  protected LinearLayout debugRootView;
  protected TextView debugTextView;
  protected @Nullable ExoPlayer player;

  private boolean isShowingTrackSelectionDialog;
  private Button selectTracksButton;
  private DataSource.Factory dataSourceFactory;
  private List<MediaItem> mediaItems;
  private DefaultTrackSelector trackSelector;
  private DefaultTrackSelector.Parameters trackSelectionParameters;
  private DebugTextViewHelper debugViewHelper;
  private TracksInfo lastSeenTracksInfo;
  private boolean startAutoPlay;
  private int startItemIndex;
  private long startPosition;

  // For ad playback only.

  @Nullable private AdsLoader clientSideAdsLoader;
  @Nullable private ImaServerSideAdInsertionMediaSource.AdsLoader serverSideAdsLoader;
  private ImaServerSideAdInsertionMediaSource.AdsLoader.@MonotonicNonNull State
      serverSideAdsLoaderState;

  // Mux SDK integration
  private MuxStatsExoPlayer muxStats;

  // Activity lifecycle.

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    dataSourceFactory = DemoUtil.getDataSourceFactory(/* context= */ this);

    setContentView();
    debugRootView = findViewById(R.id.controls_root);
    debugTextView = findViewById(R.id.debug_text_view);
    selectTracksButton = findViewById(R.id.select_tracks_button);
    selectTracksButton.setOnClickListener(this);

    playerView = findViewById(R.id.player_view);
    playerView.setControllerVisibilityListener(this);
    playerView.setErrorMessageProvider(new PlayerErrorMessageProvider());
    playerView.requestFocus();

    if (savedInstanceState != null) {
      // Restore as DefaultTrackSelector.Parameters in case ExoPlayer specific parameters were set.
      trackSelectionParameters =
          DefaultTrackSelector.Parameters.CREATOR.fromBundle(
              savedInstanceState.getBundle(KEY_TRACK_SELECTION_PARAMETERS));
      startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY);
      startItemIndex = savedInstanceState.getInt(KEY_ITEM_INDEX);
      startPosition = savedInstanceState.getLong(KEY_POSITION);
      Bundle adsLoaderStateBundle = savedInstanceState.getBundle(KEY_SERVER_SIDE_ADS_LOADER_STATE);
      if (adsLoaderStateBundle != null) {
        serverSideAdsLoaderState =
            ImaServerSideAdInsertionMediaSource.AdsLoader.State.CREATOR.fromBundle(
                adsLoaderStateBundle);
      }
    } else {
      trackSelectionParameters =
          new DefaultTrackSelector.ParametersBuilder(/* context= */ this).build();
      clearStartPosition();
    }
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
    super.onNewIntent(intent);
    releasePlayer();
    releaseClientSideAdsLoader();
    clearStartPosition();
    setIntent(intent);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (Util.SDK_INT > 23) {
      initializePlayer();
      if (playerView != null) {
        playerView.onResume();
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (Util.SDK_INT <= 23 || player == null) {
      initializePlayer();
      if (playerView != null) {
        playerView.onResume();
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      if (playerView != null) {
        playerView.onPause();
      }
      releasePlayer();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      if (playerView != null) {
        playerView.onPause();
      }
      releasePlayer();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    releaseClientSideAdsLoader();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (grantResults.length == 0) {
      // Empty results are triggered if a permission is requested while another request was already
      // pending and can be safely ignored in this case.
      return;
    }
    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      initializePlayer();
    } else {
      showToast(R.string.storage_permission_denied);
      finish();
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    updateTrackSelectorParameters();
    updateStartPosition();
    outState.putBundle(KEY_TRACK_SELECTION_PARAMETERS, trackSelectionParameters.toBundle());
    outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay);
    outState.putInt(KEY_ITEM_INDEX, startItemIndex);
    outState.putLong(KEY_POSITION, startPosition);
    if (serverSideAdsLoaderState != null) {
      outState.putBundle(KEY_SERVER_SIDE_ADS_LOADER_STATE, serverSideAdsLoaderState.toBundle());
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
    if (view == selectTracksButton
        && !isShowingTrackSelectionDialog
        && TrackSelectionDialog.willHaveContent(trackSelector)) {
      isShowingTrackSelectionDialog = true;
      TrackSelectionDialog trackSelectionDialog =
          TrackSelectionDialog.createForTrackSelector(
              trackSelector,
              /* onDismissListener= */ dismissedDialog -> isShowingTrackSelectionDialog = false);
      trackSelectionDialog.show(getSupportFragmentManager(), /* tag= */ null);
    }
  }

  // StyledPlayerControlView.VisibilityListener implementation

  @Override
  public void onVisibilityChange(int visibility) {
    debugRootView.setVisibility(visibility);
  }

  // Internal methods

  protected void setContentView() {
    setContentView(R.layout.player_activity);
  }

  /** @return Whether initialization was successful. */
  protected boolean initializePlayer() {
    if (player == null) {
      Intent intent = getIntent();

      mediaItems = createMediaItems(intent);
      if (mediaItems.isEmpty()) {
        return false;
      }

      boolean preferExtensionDecoders =
          intent.getBooleanExtra(IntentUtil.PREFER_EXTENSION_DECODERS_EXTRA, false);
      RenderersFactory renderersFactory =
          DemoUtil.buildRenderersFactory(/* context= */ this, preferExtensionDecoders);

      trackSelector = new DefaultTrackSelector(/* context= */ this);
      lastSeenTracksInfo = TracksInfo.EMPTY;
      player =
          new ExoPlayer.Builder(/* context= */ this)
              .setRenderersFactory(renderersFactory)
              .setMediaSourceFactory(createMediaSourceFactory())
              .setTrackSelector(trackSelector)
              .build();
      player.setTrackSelectionParameters(trackSelectionParameters);
      player.addListener(new PlayerEventListener());
      player.addAnalyticsListener(new EventLogger(trackSelector));
      player.setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true);
      player.setPlayWhenReady(startAutoPlay);
      playerView.setPlayer(player);
      serverSideAdsLoader.setPlayer(player);
      debugViewHelper = new DebugTextViewHelper(player, debugTextView);
      debugViewHelper.start();

      CustomerPlayerData customerPlayerData = new CustomerPlayerData();
      CustomerVideoData customerVideoData = new CustomerVideoData();
      customerVideoData.setVideoTitle(intent.getStringExtra("VIDEO_TITLE_EHERE"));
      CustomData customData = new CustomData();
      customData.setCustomData1("YOUR_CUSTOM_STRING_HERE");
      CustomerData customerData = new CustomerData(customerPlayerData, customerVideoData, null);
      customerData.setCustomData(customData);
      muxStats = new MuxStatsExoPlayer(
          this, "YOUR_ENVIRONMENT_KEY_HERE", player, playerView, customerData);
      muxStats.enableMuxCoreDebug(true, false);
    }
    boolean haveStartPosition = startItemIndex != C.INDEX_UNSET;
    if (haveStartPosition) {
      player.seekTo(startItemIndex, startPosition);
    }
    player.setMediaItems(mediaItems, /* resetPosition= */ !haveStartPosition);
    player.prepare();
    updateButtonVisibility();
    return true;
  }

  private MediaSource.Factory createMediaSourceFactory() {
    ImaServerSideAdInsertionMediaSource.AdsLoader.Builder serverSideAdLoaderBuilder =
        new ImaServerSideAdInsertionMediaSource.AdsLoader.Builder(/* context= */ this, playerView);
    if (serverSideAdsLoaderState != null) {
      serverSideAdLoaderBuilder.setAdsLoaderState(serverSideAdsLoaderState);
    }
    serverSideAdsLoader = serverSideAdLoaderBuilder.build();
    ImaServerSideAdInsertionMediaSource.Factory imaServerSideAdInsertionMediaSourceFactory =
        new ImaServerSideAdInsertionMediaSource.Factory(
            serverSideAdsLoader, new DefaultMediaSourceFactory(dataSourceFactory));
    return new DefaultMediaSourceFactory(dataSourceFactory)
        .setAdsLoaderProvider(this::getClientSideAdsLoader)
        .setAdViewProvider(playerView)
        .setServerSideAdInsertionMediaSourceFactory(imaServerSideAdInsertionMediaSourceFactory);
  }

  private List<MediaItem> createMediaItems(Intent intent) {
    String action = intent.getAction();
    boolean actionIsListView = IntentUtil.ACTION_VIEW_LIST.equals(action);
    if (!actionIsListView && !IntentUtil.ACTION_VIEW.equals(action)) {
      showToast(getString(R.string.unexpected_intent_action, action));
      finish();
      return Collections.emptyList();
    }

    List<MediaItem> mediaItems =
        createMediaItems(intent, DemoUtil.getDownloadTracker(/* context= */ this));
    for (int i = 0; i < mediaItems.size(); i++) {
      MediaItem mediaItem = mediaItems.get(i);

      if (!Util.checkCleartextTrafficPermitted(mediaItem)) {
        showToast(R.string.error_cleartext_not_permitted);
        finish();
        return Collections.emptyList();
      }
      if (Util.maybeRequestReadExternalStoragePermission(/* activity= */ this, mediaItem)) {
        // The player will be reinitialized if the permission is granted.
        return Collections.emptyList();
      }

      MediaItem.DrmConfiguration drmConfiguration = mediaItem.localConfiguration.drmConfiguration;
      if (drmConfiguration != null) {
        if (Util.SDK_INT < 18) {
          showToast(R.string.error_drm_unsupported_before_api_18);
          finish();
          return Collections.emptyList();
        } else if (!FrameworkMediaDrm.isCryptoSchemeSupported(drmConfiguration.scheme)) {
          showToast(R.string.error_drm_unsupported_scheme);
          finish();
          return Collections.emptyList();
        }
      }
    }
    return mediaItems;
  }

  private AdsLoader getClientSideAdsLoader(MediaItem.AdsConfiguration adsConfiguration) {
    // The ads loader is reused for multiple playbacks, so that ad playback can resume.
    if (clientSideAdsLoader == null) {
      clientSideAdsLoader = new ImaAdsLoader.Builder(/* context= */ this)
          /*
           * This replaces `monitorImaAdsLoader` method because in r2.12.x ImaAdsLoader
           * will create google.v3.AdsLoader on adRequest, which means that monitorImaAdsLoader
           * Will always receive null pointer and will be unable to recieve add events.
           */
          .setAdErrorListener(muxStats.getAdsImaSdkListener())
          .setAdEventListener(muxStats.getAdsImaSdkListener())
          .build();
    }
    clientSideAdsLoader.setPlayer(player);
    return clientSideAdsLoader;
  }

  protected void releasePlayer() {
    if (player != null) {
      updateTrackSelectorParameters();
      updateStartPosition();
      serverSideAdsLoaderState = serverSideAdsLoader.release();
      serverSideAdsLoader = null;
      debugViewHelper.stop();
      debugViewHelper = null;
      player.release();
      player = null;
      playerView.setPlayer(/* player= */ null);
      mediaItems = Collections.emptyList();
    }
    if (clientSideAdsLoader != null) {
      clientSideAdsLoader.setPlayer(null);
    } else {
      playerView.getAdViewGroup().removeAllViews();
    }
  }

  private void releaseClientSideAdsLoader() {
    if (clientSideAdsLoader != null) {
      clientSideAdsLoader.release();
      clientSideAdsLoader = null;
      playerView.getAdViewGroup().removeAllViews();
    }
  }

  private void updateTrackSelectorParameters() {
    if (player != null) {
      // Until the demo app is fully migrated to TrackSelectionParameters, rely on ExoPlayer to use
      // DefaultTrackSelector by default.
      trackSelectionParameters =
          (DefaultTrackSelector.Parameters) player.getTrackSelectionParameters();
    }
  }

  private void updateStartPosition() {
    if (player != null) {
      startAutoPlay = player.getPlayWhenReady();
      startItemIndex = player.getCurrentMediaItemIndex();
      startPosition = Math.max(0, player.getContentPosition());
    }
  }

  protected void clearStartPosition() {
    startAutoPlay = true;
    startItemIndex = C.INDEX_UNSET;
    startPosition = C.TIME_UNSET;
  }

  // User controls

  private void updateButtonVisibility() {
    selectTracksButton.setEnabled(
        player != null && TrackSelectionDialog.willHaveContent(trackSelector));
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

  private class PlayerEventListener implements Player.Listener {

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
      if (playbackState == Player.STATE_ENDED) {
        showControls();
      }
      updateButtonVisibility();
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
        player.seekToDefaultPosition();
        player.prepare();
      } else {
        updateButtonVisibility();
        showControls();
      }
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public void onTracksInfoChanged(TracksInfo tracksInfo) {
      updateButtonVisibility();
      if (tracksInfo == lastSeenTracksInfo) {
        return;
      }
      if (!tracksInfo.isTypeSupportedOrEmpty(
          C.TRACK_TYPE_VIDEO, /* allowExceedsCapabilities= */ true)) {
        showToast(R.string.error_unsupported_video);
      }
      if (!tracksInfo.isTypeSupportedOrEmpty(
          C.TRACK_TYPE_AUDIO, /* allowExceedsCapabilities= */ true)) {
        showToast(R.string.error_unsupported_audio);
      }
      lastSeenTracksInfo = tracksInfo;
    }
  }

  private class PlayerErrorMessageProvider implements ErrorMessageProvider<PlaybackException> {

    @Override
    public Pair<Integer, String> getErrorMessage(PlaybackException e) {
      String errorString = getString(R.string.error_generic);
      Throwable cause = e.getCause();
      if (cause instanceof DecoderInitializationException) {
        // Special case for decoder initialization failures.
        DecoderInitializationException decoderInitializationException =
            (DecoderInitializationException) cause;
        if (decoderInitializationException.codecInfo == null) {
          if (decoderInitializationException.getCause() instanceof DecoderQueryException) {
            errorString = getString(R.string.error_querying_decoders);
          } else if (decoderInitializationException.secureDecoderRequired) {
            errorString =
                getString(
                    R.string.error_no_secure_decoder, decoderInitializationException.mimeType);
          } else {
            errorString =
                getString(R.string.error_no_decoder, decoderInitializationException.mimeType);
          }
        } else {
          errorString =
              getString(
                  R.string.error_instantiating_decoder,
                  decoderInitializationException.codecInfo.name);
        }
      }
      return Pair.create(0, errorString);
    }
  }

  private static List<MediaItem> createMediaItems(Intent intent, DownloadTracker downloadTracker) {
    List<MediaItem> mediaItems = new ArrayList<>();
    for (MediaItem item : IntentUtil.createMediaItemsFromIntent(intent)) {
      @Nullable
      DownloadRequest downloadRequest =
          downloadTracker.getDownloadRequest(item.localConfiguration.uri);
      if (downloadRequest != null) {
        MediaItem.Builder builder = item.buildUpon();
        builder
            .setMediaId(downloadRequest.id)
            .setUri(downloadRequest.uri)
            .setCustomCacheKey(downloadRequest.customCacheKey)
            .setMimeType(downloadRequest.mimeType)
            .setStreamKeys(downloadRequest.streamKeys);
        @Nullable
        MediaItem.DrmConfiguration drmConfiguration = item.localConfiguration.drmConfiguration;
        if (drmConfiguration != null) {
          builder.setDrmConfiguration(
              drmConfiguration.buildUpon().setKeySetId(downloadRequest.keySetId).build());
        }

        mediaItems.add(builder.build());
      } else {
        mediaItems.add(item);
      }
    }
    return mediaItems;
  }
}
