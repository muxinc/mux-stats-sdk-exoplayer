package com.mux.stats.sdk.muxstats.automatedtests.mockup.http;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleHTTPServer extends Thread {

  private boolean isRunning;
  private final int port;
  private final int bandwidthLimit;
  private long networkJamEndPeriod = -1;
  private int networkJamFactor = 1;
  private int seekLatency;
  private boolean constantJam = false;
  private long manifestDelay = 0;

  private final ServerSocket server;
  ArrayList<ConnectionWorker> workers = new ArrayList<>();

  Lock serverLock = new ReentrantLock();
  Condition serverDied = serverLock.newCondition();

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

  /*
   * This is the number of MS that will be waited before serving data for requested range
   */
  public void setSeekLatency(int latency) {
    seekLatency = latency;
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

  public void run() {
    isRunning = true;
    while (isRunning) {
      try {
        acceptConnection();
//            } catch (InterruptedException e) {
//                // Ignore, sombody killed the server
      } catch (IOException e) {
        // TODO handle this
        e.printStackTrace();
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
    workers.add(new ConnectionWorker(clientSocket, bandwidthLimit,
        networkJamEndPeriod, networkJamFactor, constantJam, seekLatency, manifestDelay));
  }
}
