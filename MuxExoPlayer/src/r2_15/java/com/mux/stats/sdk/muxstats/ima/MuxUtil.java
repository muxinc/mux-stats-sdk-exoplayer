package com.mux.stats.sdk.muxstats.ima;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

public class MuxUtil {

  public static byte[] readToEnd(DataSource dataSource) throws IOException {
    return Util.readToEnd(dataSource);
  }

  /**
   * Converts a time in milliseconds to the corresponding time in microseconds, preserving {@link
   * C#TIME_UNSET} values and {@link C#TIME_END_OF_SOURCE} values.
   *
   * @param timeMs The time in milliseconds.
   * @return The corresponding time in microseconds.
   */
  public static long msToUs(long timeMs) {
    return (timeMs == C.TIME_UNSET || timeMs == C.TIME_END_OF_SOURCE) ? timeMs : (timeMs * 1000);
  }

  /**
   * Converts a time in microseconds to the corresponding time in milliseconds, preserving {@link
   * C#TIME_UNSET} and {@link C#TIME_END_OF_SOURCE} values.
   *
   * @param timeUs The time in microseconds.
   * @return The corresponding time in milliseconds.
   */
  public static long usToMs(long timeUs) {
    return (timeUs == C.TIME_UNSET || timeUs == C.TIME_END_OF_SOURCE) ? timeUs : (timeUs / 1000);
  }
}
