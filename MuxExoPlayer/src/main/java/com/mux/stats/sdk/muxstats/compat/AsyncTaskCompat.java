package com.mux.stats.sdk.muxstats.compat;

import android.os.AsyncTask;
import android.os.Build;

/**
 * Helper for accessing features in {@link android.os.AsyncTask}
 * introduced after API level 4 in a backwards compatible fashion.
 */
public class AsyncTaskCompat {
    /**
     * Executes the task with the specified parameters, allowing multiple tasks to run in parallel
     * on a pool of threads managed by {@link android.os.AsyncTask}.
     *
     * @param task The {@link android.os.AsyncTask} to execute.
     * @param params The parameters of the task.
     * @return the instance of AsyncTask.
     */
    public static <Params, Progress, Result> AsyncTask<Params, Progress, Result> executeParallel(
            AsyncTask<Params, Progress, Result> task,
            Params... params) {
        if (task == null) {
            throw new IllegalArgumentException("task can not be null");
        }
        if (Build.VERSION.SDK_INT >= 11) {
            // From API 11 onwards, we need to manually select the THREAD_POOL_EXECUTOR
            AsyncTaskCompatHoneycomb.executeParallel(task, params);
        } else {
            // Before API 11, all tasks were run in parallel
            task.execute(params);
        }
        return task;
    }
}
