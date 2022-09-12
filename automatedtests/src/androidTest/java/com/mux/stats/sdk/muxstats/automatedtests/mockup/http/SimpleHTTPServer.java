package com.mux.stats.sdk.muxstats.automatedtests.mockup.http;


import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleHTTPServer extends Thread implements ConnectionListener {

  private static final String TAG = SimpleHTTPServer.class.getSimpleName();

  public static String FILE_NAME_RESPONSE_HEADER = "Automated-Test-File-Name";
  public static String REQUEST_UUID_HEADER = "Request-segment-uuid";
  public static String X_CDN_RESPONSE_HEADER = "x-cdn";
  public static String CONTENT_TYPE_RESPONSE_HEADER = "Content-Type";
  public static String REQUEST_NETWORK_DELAY_HEADER = "Request-Delay";

  private boolean isRunning;
  private final int port;
  private final int bandwidthLimit;
  private long networkJamEndPeriod = -1;
  private int networkJamFactor = 1;
  private int seekLatency;
  private boolean constantJam = false;
  private long manifestDelay = 0;
  HashMap<String, String> additionalHeaders = new HashMap<>();
  HashMap<String, SegmentStatistics> segmentsServed = new HashMap<>();

  private final ServerSocket server;
  LinkedBlockingDeque<ConnectionWorker> workers = new LinkedBlockingDeque<>();

  Lock serverLock = new ReentrantLock();
  Condition serverDied = serverLock.newCondition();
  Condition newMediaSegmentStarted = serverLock.newCondition();

  /*
   * Run a server on localhost:port
   */
  public SimpleHTTPServer(int port, int bandwidthLimit) throws IOException {
    this.port = port;
    this.bandwidthLimit = bandwidthLimit;
    server = new ServerSocket(port);
    seekLatency = 0;
    start();
  }

  // Used in automated tests
  public boolean waitForNextSegmentToLoad(long timeoutInMs) {
    try {
      serverLock.lock();
      return newMediaSegmentStarted.await(timeoutInMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Log.e(TAG, "Interrupted while waiting for next segment", e);
      return false;
    } finally {
      serverLock.unlock();
    }
  }

  /*
   * This is the number of MS that will be waited before serving data for requested range
   */
  public void setSeekLatency(int latency) {
    seekLatency = latency;
  }

  public SegmentStatistics getSegmentStatistics(String segmentUuid) {
    return segmentsServed.get(segmentUuid);
  }

  public void jamNetwork(long jamPeriod, int jamFactor, boolean constantJam) {
    this.networkJamEndPeriod = System.currentTimeMillis() + jamPeriod;
    this.networkJamFactor = jamFactor;
    this.constantJam = constantJam;
    for (ConnectionWorker worker : workers) {
      worker.jamNetwork(jamPeriod, jamFactor, constantJam);
    }
  }

  public void setHLSManifestDelay(long manifestDelay) {
    this.manifestDelay = manifestDelay;
    for (ConnectionWorker worker : workers) {
      worker.setNetworkDelay(manifestDelay);
    }
  }

  public void setAdditionalHeader(String headerName, String headerValue) {
    additionalHeaders.put(headerName, headerValue);
  }

  public void run() {
    isRunning = true;
    while (isRunning) {
      try {
        acceptConnection();
//            } catch (InterruptedException e) {
//                // Ignore, sombody killed the server
      } catch (IOException e) {
        // TODO handle this
        Log.e(TAG, "thrown while acceptConnection()", e);
      }
    }
    for (ConnectionWorker worker : workers) {
      worker.kill();
    }
    serverLock.lock();
    serverDied.signalAll();
    serverLock.unlock();
  }

  public void kill() {
    isRunning = false;
    try {
      server.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    // Wait for server to die
    try {
      serverLock.lock();
      serverDied.await(10000, TimeUnit.MILLISECONDS);
      serverLock.unlock();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /*
   * Process HTTP connections
   */
  private void acceptConnection() throws IOException {
    Socket clientSocket = server.accept();
    workers.add(new ConnectionWorker(this, clientSocket, bandwidthLimit,
        networkJamEndPeriod, networkJamFactor, constantJam, seekLatency, manifestDelay,
        additionalHeaders));
  }

  @Override
  public void segmentServed(String requestUuid, SegmentStatistics segmentStat) {
    segmentsServed.put(requestUuid, segmentStat);
    serverLock.lock();
    newMediaSegmentStarted.signalAll();
    serverLock.unlock();
  }
}
