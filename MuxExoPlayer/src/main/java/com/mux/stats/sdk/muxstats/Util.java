package com.mux.stats.sdk.muxstats;

import java.util.List;

/*package*/ class Util {
  private Util() {
    throw new RuntimeException("no instances");
  }

  /**
   * Safely casts some object to a subclass of it
   * Returns null if the input object was null, or if the input object was not an instance of the
   * input class.
   * <p>
   * This method implements the Kotlin `as?` operator
   */
  public static <T, R extends T> R safeCast(T it, Class<R> rClass) {
    return (it != null && rClass.isInstance(it)) ? rClass.cast(it) : null;
  }

  /**
   * Filters only the items satisfying the predicate into the supplied List
   *
   * @param iter
   * @param out
   * @param predicate
   * @param <T>
   * @return
   */
  public static <T> List<T> filter(Iterable<T> iter, List<T> out, Function<T, Boolean> predicate) {
    for (T t : iter) {
      if (predicate.apply(t) == Boolean.TRUE) {
        out.add(t);
      }
    }

    return out;
  }

  /*package*/ interface Function<T, R> {
    R apply(T t);
  }

}
