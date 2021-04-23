package com.mux.stats.sdk.muxstats.automatedtests.mockup.http;

public interface ConnectionListener {

  public void segmentServed(String requestUuid, SegmentStatistics segmentStat);

}
