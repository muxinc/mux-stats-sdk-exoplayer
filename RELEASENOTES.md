# Release notes

## v2.4.14
 - Support Exoplayer 2.14 (#134)

## v2.4.13
 - Add CustomerData class to Proguard (#131)

## v2.4.12
 - Add checkstyle task to gradle (#123)
 - Replaced FrameRendererListener with VideoListener. (#127)
 - Custom data update: deprecate MuxExoPlayer constructors that take a CustomerData arg separately, add custom-dimensions example to demo app (#128)

## v2.4.11
 - Run automated tests on real devices (#121)
 - Fix MIME-type detection for HLS & DASH stream by allowing the server to make that determination. (#122)
 - Upgrade MuxCore to 6.6.0, which includes:
   - Add support for custom dimensions in view metadata
   - Fix propagation of bandwidth metrics data by sending even when unchanged

## v2.4.10
 - Fix an issue where the application may crash while using DASH playback sources

## v2.4.9
 - Added support for CDN header tracking, including mid-stream CDN switching. (#109)
 - Fix a null-pointer crash in the ads listener. (#112)
 - Updated the Mux Core library, added support for bandwidth metrics.

## v2.4.8
 - Reset internal state when calling videochange, fixing an issue where rebuffering may be reported incorrectly after calling videochange. (#107)

## v2.4.7
 - Use playWhenReady and playbackStartPosition in Exoplayer r2.13 test. (#105)

## v2.4.6
 - Fix for use case when playback starts with the seek. (#99)
 - Upgrade MuxCore to 6.3.0, which includes:
   - Reset error-tracking state when loading a new video.

## v2.4.5
 - Added support for Exoplayer version 2.13.1 (#98)

## v2.4.4
 - Removed all content from res directory under MuxExoPlayer. (#92)
 - Added test for playback end events and view end event. (#94)
 - Reformat code with Google Java style (#88)
 - Upgrade MuxCore to 6.2.0, which includes:
   - Added viewEnd event on player release.

## v2.4.3
 - Propagate `customerViewData` through the constructors for 2.9.6, 2.11.1, and 2.12.1 as well.

## v2.4.2
 - Fix propagation of `customerViewData` through all constructors.

## v2.4.1
 - Fix detection of rebuffering after seeking
 - Use a random UUID stored in shared preferences for mux_viewer_id
 - Fix sending of `view_session_id`

## v2.4.0
 - Fix an issue where additional icons and image files were included
 - Fix an issue where the application would crash on Android 11
 - Expose additional fatal playback errors

## v2.3.1
 - Fix an issue where AAR file size was too large due to inadverting inclusion of a video file

## v2.3.0
 - Fix an issue where logical resolution was calculated incorrectly
 - Report `wired` instead of `ethernet` for certain connection types
 - [internal] Integrate automated integration tests

## v2.2.0
 - Upgrade to Android Studio 4.1
 - Upgrade to Gradle 6.1.1
 - Update dockerfile and build script for new tooling
 - Support back to minAPI 16 via multidexing support

## v2.1.0
 - Support ExoPlayer r2.12.x flavors
 - Expose CustomerViewData through ProGuard
 - Ensure packages are scoped to com.mux.stats.sdk in ProGuard
 - Update version reported by the plugin (v2.0.0 reported v1.5.0 unintentionally, now will report v2.1.0)
 - Fix an issue where accessing ad integration could cause a crash
 - Bump to MuxCore v6.0.0
 - Fix invalid rebuffering reported for audio-only and playback
 - Ensure that events are sent in a more timely manner (some events are held after a PauseEvent until
the next active event)

## v2.0.0
 - Bump to v5.0.0 of MuxCore
   - Update ad handling logic to ensure that ad metrics and dimensions are tracked correctly
   - Retry sending failed beacons, rather than letting them drop
   - Fix issue where we were incorrectly calculating scaling metrics when screen or video resolution was negative
   - Fix an issue where watch time is incorrectly increasing after certain events
   - Make sure that time to first frame is not tracked for views that result from `programchange`
   - Add support for `viewer_connection_type`, which is a breaking change for `IDevice`, as it adds another method that must be implemented
   - Add support for `view_session_id`, which includes an additional `CustomerViewData` class. This changes the constructor for creating a `MuxStats` instance
 - Drop support for ExoPlayer r2.7.x and r2.8.x
 - Implement SeekingEvent directly in `MuxStatsExoPlayer`
 - Fix issue where source type could be null and cause a crash
 - Fix an issue where ad events are sent out of order in some cases
 - Add connection type detection
 - Report logical sizes for player size, rather than physical size
 - Fix an issue where time to first frame was incorrectly measured in some cases, such as mid- or post-roll ad playback without a pre-roll
 - Add support for `CustomerViewData`, including `setViewSessionId`

## v1.5.0
 - Fix an issue where if you were using `muxStatsExoPlayer.setPlayerSize(width, height)` those values were not used correctly. Note: If you call this, you must update the player size whenever that changes, as the SDK will no longer pull those values automatically.

## v1.4.0
 - Move `MuxSDKViewOrientation` to `com.mux.stats.sdk.core.MuxSDKViewOrientation` and expose it publicly

## v1.3.0
 - Add support for `RenditionChangeEvent`, which is tracked automatically
 - Add support for `OrientationChangeEvent`, which can be triggered by calling `muxStatsExoPlayer.orientationChange(MuxSDKViewOrientation orientation)`. Supported orientations are `MuxSDKViewOrientation.LANDSCAPE` and `MuxSDKViewOrientation.PORTRAIT`.
 - Fix an issue where fullscreen tracking was not working correctly

## v1.2.0
 - Add support for ExoPlayer 2.11.x
 - Note: there is a known issue right now with ExoPlayer r2.11.x where ads are not tracked correctly. This is under development.

## v1.1.0
 - Add support for additional debug logging. See `muxStatsExoPlayer.enableMuxCoreDebug(Boolean enable, Boolean verbose)`
 - Add the ability to update customerVideoData and customerPlayerData mid-stream, in cases that certain metadata may not be available at the beginning of playback. See `muxStatsExoPlayer.updateCustomerData(CustomerPlayerData customerPlayerData, CustomerVideoData customerVideoData)`
 - Fix an issue where if `MuxStatsExoPlayer` is initialized too late, the stream is not tracked correctly
 - Fix an issue where Mux Plugin Version is reported incorrectly
 - Fix an issue where the `EndedEvent` is not sent to the backend
 - Fix an issue where tracking playback is not correct when playWhenReady is set to false (i.e. non-autoplay playback)
 - Fix an issue where events could be sent after playback completes, forcing the view to be active for longer than it actually was
 - Utilize more accurate client timestamps for event timing

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
