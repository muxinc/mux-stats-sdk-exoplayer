package com.mux.stats.sdk.muxstats.automatedtests;

import android.util.Log;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class RetryTest implements TestRule {

  static final String TAG = "MuxStatsTestRule";

  private int retryCount;

  public RetryTest(int retryCount) {
    this.retryCount = retryCount;
  }

  public Statement apply(Statement base, Description description) {
    return statement(base, description);
  }

  private Statement statement(final Statement base, final Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        Throwable caughtThrowable = null;

        // implement retry logic here
        for (int i = 0; i <= retryCount; i++) {
          try {
            base.evaluate();
            return;
          } catch (Throwable t) {
            caughtThrowable = t;
            Log.e(TAG, description.getDisplayName() + ": run " + (i + 1) + " failed");
          }
        }
        Log.e(TAG, description.getDisplayName() + ": giving up after " + retryCount + " failures");
        throw caughtThrowable;
      }
    };
  }
}

