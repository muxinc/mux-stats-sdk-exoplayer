# Mux Stats SDK for ExoPlayer

This is the Mux wrapper around ExoPlayer, built on top of Mux's core Java library, providing Mux
Data performance analytics for applications utilizing
[Google's ExoPlayer](https://github.com/google/ExoPlayer).

## Usage

See full integration instructions
here: [https://docs.mux.com/guides/data/monitor-exoplayer](https://docs.mux.com/guides/data/monitor-exoplayer)

Add our maven repository

```groovy
repositories {
  maven {
    url "https://muxinc.jfrog.io/artifactory/default-maven-release-local"
  }
}
```

Add a dependency compatible with your version of ExoPlayer. The full list of supported versions can
be found [here](https://docs.mux.com/guides/data/monitor-exoplayer#1-install-the-mux-data-sdk)

```groovy
api 'com.mux.stats.sdk.muxstats:MuxExoPlayer_(ExoPlayer SDK version with underscores):(Mux SDK version)'
```

Monitor your ExoPlayer

```kotlin
muxStatsExoPlayer = exoPlayer.monitorWithMuxData(
      context = requireContext(),
      envKey = "YOUR_ENV_KEY_HERE",
      playerView = playerView,
      customerData = customerData
    )
```

For more
information, [check out the integration guide](https://docs.mux.com/guides/data/monitor-exoplayer)

## Contributing

The code in this repo conforms to
the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html). Run the
reformatter on files before committing.

The code was formatted in Android Studio/IntelliJ using
the [Google Java Style for IntelliJ](https://github.com/google/styleguide/blob/gh-pages/intellij-java-google-style.xml)
. The style can be installed via the Java-style section of the IDE
preferences (`Editor -> Code Style - >Java`).

## Documentation

See [our docs](https://docs.mux.com/docs/exoplayer-integration-guide) for more information.
