# Release notes

## v1.0.0
- Add support for ExoPlayer 2.9.x
- Add support for ExoPlayer 2.10.x
- Fix issue where ExoPlayer versions 2.9.x and greater would log messages about accessing the player on the wrong thread
- Removed support for ExoPlayer 2.6.x and older (due to changes in build pipeline and Gradle configurations)
- Support Gradle 3.5.2

## v0.5.1
- Allow customers to disable Sentry reporting for exceptions
- Clean up demo application slightly

## v0.5.0
- Deprecated method `muxStatsExoPlayer.getImaSDKListener` in favor of `muxStatsExoPlayer.monitorImaAdsLoader(adsLoader)`. The previous method will still work, but you should migrate to the new method as the deprecated method will be removed with th next major version.
- Fix an issue where Google IMA SDK was a hard requirement unintentionally.

## v0.4.3
 - Fix an issue where a NullPointerException may occur during playback of a video while tracking bandwidth metrics.

## v0.4.2
- Added API method `programChange(CustomerVideoData customerVideoData)`, for use when inside of a single stream the program changes. For instance, in a 24/7 live stream, you may have metadata indicating program changes which should be tracked as separate views within Mux. Previously, `videoChange` might have been used for this case, but this would not work correctly, and you would not necessarily have seen the subsequent views show up. See [the documentation](https://docs.mux.com/docs/android-integration-guide#section-6-changing-the-video) for full explanation.
- Fixed a bug where under poor network conditions, an exception raised as a result of a network request could result in not tracking the view correctly subsequently (such as missing rebuffer tracking after this point).

## v0.4.1
- Remove the listeners on the `ExoPlayer` object when `release` is called.
  - This fixes and issue where the application may crash after calling release
    if the ExoPlayer instance is removed while the SDK is still listening to
    it.

## v0.4.0
- [feature] Support bandwidth throughput metrics on video segment download
  for HLS and Dash streaming.
- **breaking change** The signature for `getAdaptiveMediaSourceEventListener`
  and `getExtractorMediaSourceEventListener` has been changed. These methods
  are used to enable throughput metrics tracking for ExoPlayer versions
  _before_ r2.8.0, and now require that the streaming protocol type is
  passed as the first parameter. The type is the same as is returned from
  [this ExoPlayer API call](https://github.com/muxinc/stats-sdk-exoplayer/blob/release-v2/demo/src/main/java/com/google/android/exoplayer2/demo/PlayerActivity.java#L355).

## v0.3.0
- **breaking change** The signature for the `MuxStatsExoPlayer` constructor
  has changed, and now requires an additional parameter (the first) to be
  and Android `Context` reference.
- abstract more core logic into mux-stats-sdk-java
- [build] rename and copy build artifacts

## v0.2.2
- add back in previously missing methods to `MuxStatsExoPlayer`:
  - `videoChange`
  - `setPlayerSize`
  - `error`
  - `setAutomaticErrorTracking`

## v0.2.1
- add support for `ExoPlayer` r2.7.x
- add support for `ExoPlayer` r2.8.x
- update to v2.1.0 of mux-stats-sdk-java
