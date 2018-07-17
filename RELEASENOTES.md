# Release notes

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
