package com.mux.stats.sdk.muxstats

import android.content.Context
import android.view.View
import com.google.ads.interactivemedia.v3.api.AdsLoader
import com.google.android.exoplayer2.ExoPlayer
import com.mux.stats.sdk.core.CustomOptions
import com.mux.stats.sdk.core.model.CustomerData

/**
 * Monitor this [ExoPlayer] with Mux Data. When you clean up your player, make sure to call
 * [MuxStatsExoPlayer.release] to ensure all player-related resources are released
 *
 * If you are using Google IMA Ads, you must add our listener to your [AdsLoader] in order for the
 * Mux Data SDK to monitor ad-related events, using [MuxStatsExoPlayer.getAdsImaSdkListener]:
 * AdsLoader.Builder(this)
 *    .setAdErrorListener(muxStats.getAdsImaSdkListener())
 *    .setAdEventListener(muxStats.getAdsImaSdkListener())
 *    //...
 *    build()
 *
 * Check out our full integration instructions for more information:
 * https://docs.mux.com/guides/data/monitor-exoplayer
 *
 * @param context The context you're playing in. Screen size will be detected if this is an Activity
 * @param playerView The View the player is rendering on. For Audio-only, this can be omitted/null
 * @param customerData Data about you, your video, and your player.
 * @param customOptions Options about the behavior of the SDK. Unless you have a special use case,
 *    this can be left null/omitted
 */
@Suppress("unused")
fun ExoPlayer.monitorWithMuxData(
  context: Context,
  envKey: String,
  playerView: View? = null,
  customerData: CustomerData,
  customOptions: CustomOptions = CustomOptions()
): MuxStatsExoPlayer = MuxStatsExoPlayer(
  context = context,
  envKey = envKey,
  player = this,
  playerView = playerView,
  customerData = customerData,
  customOptions = customOptions
)

/**
 * @deprecated Please prefer [monitorWithMuxData].
 */
@Deprecated(
  message = "Please prefer providing your env key via this method",
  replaceWith = ReplaceWith("monitorWithMuxData(context, ENV_KEY, exoPlayer, playerView, customerData")
)
fun ExoPlayer.monitorWithMuxData(
  context: Context,
  playerView: View? = null,
  playerName: String,
  customerData: CustomerData,
  customOptions: CustomOptions = CustomOptions()
): MuxStatsExoPlayer = MuxStatsExoPlayer(
  context = context,
  player = this,
  playerView = playerView,
  playerName = playerName,
  customerData = customerData,
  customOptions = customOptions
)
