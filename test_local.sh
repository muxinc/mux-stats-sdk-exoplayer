#! /bin/sh

# test_local.sh: Runs Buildkite's test suite on your local machine

./gradlew :automatedtests:connectedR2_10_6NotFatAarDebugAndroidTest \
	:automatedtests:connectedR2_11_1NotFatAarDebugAndroidTest \
	:automatedtests:connectedR2_12_1NotFatAarDebugAndroidTest \
	:automatedtests:connectedR2_13_1NotFatAarDebugAndroidTest \
	:automatedtests:connectedR2_14_1NotFatAarDebugAndroidTest \
	:automatedtests:connectedR2_15_1NotFatAarDebugAndroidTest \
	:automatedtests:connectedR2_16_1NotFatAarDebugAndroidTest \
	:automatedtests:connectedR2_9_6NotFatAarDebugAndroidTest

