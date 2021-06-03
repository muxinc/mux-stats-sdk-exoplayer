package com.mux.stats.sdk.muxstats.automatedtests.mockup.http;

public class SegmentStatistics {

  String segmentFileName;
  long segmentRequestedAt;
  long segmentRespondedAt;
  long segmentLengthInBytes;
  long segmentVideoWidth;
  long segmentVideoHeight;
  long segmentMediaDuration;
  // Start time relative to complete presentation duration.
  long SegmentMediaStartTime;

  public long getSegmentRequestedAt() {
    return segmentRequestedAt;
  }

  public void setSegmentRequestedAt(long segmentRequestedAt) {
    this.segmentRequestedAt = segmentRequestedAt;
  }

  public String getSegmentFileName() {
    return segmentFileName;
  }

  public void setSegmentFileName(String segmentFileName) {
    this.segmentFileName = segmentFileName;
  }

  public long getSegmentRespondedAt() {
    return segmentRespondedAt;
  }

  public void setSegmentRespondedAt(long segmentRespondedAt) {
    this.segmentRespondedAt = segmentRespondedAt;
  }

  public long getSegmentLengthInBytes() {
    return segmentLengthInBytes;
  }

  public void setSegmentLengthInBytes(long segmentLengthInBytes) {
    this.segmentLengthInBytes = segmentLengthInBytes;
  }

  public long getSegmentVideoWidth() {
    return segmentVideoWidth;
  }

  public void setSegmentVideoWidth(long segmentVideoWidth) {
    this.segmentVideoWidth = segmentVideoWidth;
  }

  public long getSegmentVideoHeight() {
    return segmentVideoHeight;
  }

  public void setSegmentVideoHeight(long segmentVideoHeight) {
    this.segmentVideoHeight = segmentVideoHeight;
  }

  public long getSegmentMediaDuration() {
    return segmentMediaDuration;
  }

  public void setSegmentMediaDuration(long segmentMediaDuration) {
    this.segmentMediaDuration = segmentMediaDuration;
  }

  public long getSegmentMediaStartTime() {
    return SegmentMediaStartTime;
  }

  public void setSegmentMediaStartTime(long segmentMediaStartTime) {
    SegmentMediaStartTime = segmentMediaStartTime;
  }
}
