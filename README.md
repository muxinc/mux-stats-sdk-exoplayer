# Mux Stats SDK ExoPlayer

This is the Mux wrapper around ExoPlayer, built on top of Mux's core Java library,
providing Mux Data performance analytics for applications utilizing
[Google's ExoPlayer](https://github.com/google/ExoPlayer).

This integration _prefers_ an instance of `SimpleExoPlayer` to be used and passed in, but will work with an instance of `ExoPlayer`, as well, with some limitations (see below).

## Releases

Full builds are provided as releases within the repo as versions are released. Within each release, there are multiple AAR files, one for each minor version of `ExoPlayer` that is supported. Make sure to grab the appropriate AAR for your version (e.g. r2.8.0), and make sure that the minor versions match (patch versions should not matter).

See full integration instructions here: https://docs.mux.com/docs/android-integration-guide.

## Developer Quick Start

Open this project in Android Studio, and let Gradle run to configure the application. Due to the breaking changes that occur within `ExoPlayer`'s minor version changes, this project is set up with a target for each minor version release of `ExoPlayer`, from r2.0.x up to r2.8.x. As additional versions of `ExoPlayer` are released, additional targets in this project will be added. Choose the target version that you desire, and run the demo application in the emulator to test out the functionality.

## Known Limitations
 - currently no ad events or metrics are supported
 - if an instance of `ExoPlayer` is passed, upscaling/downscaling and certain
 errors will not be tracked correctly.

## Documentation
See [our docs](https://docs.mux.com/docs/android-integration-guide) for more information.
