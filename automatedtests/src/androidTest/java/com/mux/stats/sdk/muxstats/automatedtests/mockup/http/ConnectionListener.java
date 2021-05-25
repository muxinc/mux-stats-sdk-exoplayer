package com.mux.stats.sdk.muxstats.automatedtests.mockup.http;

public interface ConnectionListener {

  void segmentServed(String requestUuid, SegmentStatistics segmentStat);

}
