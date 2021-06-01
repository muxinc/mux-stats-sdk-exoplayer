package com.mux.stats.sdk.muxstats;

public class MuxStatsHelper {
  public static void allowHeaderToBeSentToBackend(MuxStatsExoPlayer muxStats, String headerName) {
    muxStats.allowHeaderToBeSentToBackend(headerName);
  }
}