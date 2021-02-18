package com.mux.stats.sdk.muxstats.compat;

import android.os.AsyncTask;

/**
 * Implementation of AsyncTask compatibility that can call Honeycomb APIs.
 */
public class AsyncTaskCompatHoneycomb {

  public static <Params, Progress, Result> void executeParallel(
      AsyncTask<Params, Progress, Result> task,
      Params... params) {
    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
  }
}