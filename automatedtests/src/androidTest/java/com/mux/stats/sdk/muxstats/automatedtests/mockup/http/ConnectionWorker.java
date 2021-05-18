package com.mux.stats.sdk.muxstats.automatedtests.mockup.http;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;


public class ConnectionWorker extends Thread implements ConnectionListener{

  Socket clientSocket;
  int bandwidthLimit;
  boolean isRunning;
  ConnectionReceiver receiver;
  ConnectionSender sender;
  private long networkJamEndPeriod = -1;
  private int networkJamFactor = 1;
  private boolean constantJam = false;
  private long networkRequestDelay = 0;
  int seekLatency;
  ConnectionListener listener;
  HashMap<String, String> additionalHeaders = new HashMap<>();

  public ConnectionWorker(ConnectionListener listener, Socket clientSocket, int bandwidthLimit,
      long networkJamEndPeriod, int networkJamFactor,
      boolean constantJam, int seekLatency, long networkRequestDelay,
      HashMap<String, String> additionalHeaders) {
    this.listener = listener;
    this.clientSocket = clientSocket;
    this.bandwidthLimit = bandwidthLimit;
    this.constantJam = constantJam;
    this.networkJamEndPeriod = networkJamEndPeriod;
    this.networkJamFactor = networkJamFactor;
    this.seekLatency = seekLatency;
    this.networkRequestDelay = networkRequestDelay;
    this.additionalHeaders = additionalHeaders;
    start();
  }

  public void jamNetwork(long jamPeriod, int jamFactor, boolean constantJam) {
    sender.jamNetwork(jamPeriod, jamFactor, constantJam);
  }

  public void setNetworkDelay(long delay) {
    networkRequestDelay = delay;
    if (sender != null) {
      sender.setNetworkDelay(delay);
    }
  }

  public void run() {
    isRunning = true;
    try {
      receiver = new ConnectionReceiver(clientSocket.getInputStream());
      receiver.start();
      sender = new ConnectionSender(this, clientSocket.getOutputStream(), bandwidthLimit,
          networkJamEndPeriod, networkJamFactor, seekLatency, networkRequestDelay, additionalHeaders);
      sender.pause();
    } catch (IOException e) {
      e.printStackTrace();
      isRunning = false;
    }
    while (isRunning) {
      try {
        ServerAction action = receiver.getNextAction();
        sender.pause();
        if (action.getType() == ServerAction.SERVE_MEDIA_DATA) {
          sender.startServingFromPosition(action.getAssetFile(), action.getHeaders());
        }
      } catch (IOException e) {
        isRunning = false;
      } catch (InterruptedException e) {
        // Someone killed the thread
      }
    }
  }

  public void kill() {
    isRunning = false;
    interrupt();
    if (sender != null) {
      sender.kill();
    }
    if (receiver != null) {
      receiver.kill();
    }
  }

  @Override
  public void segmentServed(String requestUuid, SegmentStatistics segmentStat) {
    listener.segmentServed(requestUuid, segmentStat);
  }
}
