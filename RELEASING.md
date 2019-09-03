## Releasing

1) Create a branch on this repo for development, and add your awesome new features
2) Buildkite will automatically build each branch and create the AAR files, which
will be accessible as artifacts on each build
3)a) Once ready to go, merge to `master` if this is the first feature in a release,
with future features to be added later.
3)b) On last feature to be merged in a release, increment `versionCode` and
`versionName` with patch or minor rev in `MuxExoPlayer/build.gradle` to make the new
version.
4) Merge pull request to `master`
5) On `master`, after it has built, create a tag with a version matching what you
put in `MuxStats.java` (e.g. `git tag v0.2.1`; note the `v` in the tag version, but
_not_ in the value of `MUX_EMBED_VERISON`)
6) Buildkite will build everything you need, and once done with the version (the
artifacts should include a file that looks like `version-v0.2.1`), create a release
in this repo with the version file, all AAR files, all proguard mappings, and release
notes of what you did. You will need to manually rename all of the `mapping.txt`
proguard files, and you should name them `mapping-rX.X.X.txt` just to be consistent.

## Updating MuxCore.jar

In the case that there's a new `MuxCore.jar` library, you should copy in the updated
`MuxCore.jar` file along with the associated version file from the build, into
`MuxCore/libs/`. Once you do this, make sure to follow the release cycle above to
version the resulting AAR files.
