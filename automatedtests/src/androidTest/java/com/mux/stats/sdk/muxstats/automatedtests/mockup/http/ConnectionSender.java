package com.mux.stats.sdk.muxstats.automatedtests.mockup.http;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class ConnectionSender extends Thread {

  static final String TAG = "HTTPTestConnSender";

  OutputStream httpOut;
  InputStream assetInput;
  String assetName;
  Context context;
  boolean isPaused;
  boolean isRunning;

  long networkJammingEndPeriod = -1;
  int networkJamFactor = 1;
  boolean constantJam = false;
  Random r = new Random();

  long assetFileSize;
  long serveDataFromPosition;
  String originHeaderValue;
  long previouseDataPositionRequested;
  long bandwidthLimit;
  long seekLatency;
  boolean seekLatencyServed = false;
  byte[] transferBuffer;
  int transferBufferSize;
  long networkRequestDelay;
  ConnectionListener listener;
  SegmentStatistics segmentStat;
  HashMap<String, String> additionalHeaders = new HashMap<>();
  String requestUuid;


  public ConnectionSender(ConnectionListener listener, OutputStream httpOut, int bandwidthLimit,
      long networkJammingEndPeriod, int networkJamFactor,
      long seekLatency, long networkRequestDelay,
      HashMap<String, String> additionalHeaders) throws IOException {
    this.listener = listener;
    this.httpOut = httpOut;
    this.bandwidthLimit = bandwidthLimit;
    this.networkJammingEndPeriod = networkJammingEndPeriod;
    this.networkJamFactor = networkJamFactor;
    this.seekLatency = seekLatency;
    this.networkRequestDelay = networkRequestDelay;
    this.additionalHeaders = additionalHeaders;
    previouseDataPositionRequested = -1;

    transferBufferSize = bandwidthLimit / (8 * 100);
    transferBuffer = new byte[transferBufferSize]; // Max number of bytes to send each 10 ms
    isPaused = true;
    segmentStat = new SegmentStatistics();
    start();
  }

  public void setNetworkDelay(long delay) {
    networkRequestDelay = delay;
  }

  public void kill() {
    isRunning = false;
    interrupt();
  }

  public void pause() {
    isPaused = true;
  }


  private void parseRangeHeader(HashMap<String, String> headers) {
    this.serveDataFromPosition = 0;
    for (String headerName : headers.keySet()) {
      if (headerName.equalsIgnoreCase("Range")) {
        this.serveDataFromPosition =
            Integer.valueOf(headers.get(headerName).replaceAll("[^0-9]", ""));
        Log.i(TAG, "Got range header value: " + this.serveDataFromPosition);
      }
    }
  }

  private void parseOriginHeader(HashMap<String, String> headers) {
    this.originHeaderValue = "";
    for (String headerName : headers.keySet()) {
      if (headerName.equalsIgnoreCase("Origin")) {
        this.originHeaderValue = headers.get(headerName);
        Log.i(TAG, "Got range header value: " + this.serveDataFromPosition);
      }
    }
  }

  public void startServingFromPosition(String assetName, HashMap<String, String> headers)
      throws IOException, InterruptedException {
    this.assetName = assetName;
    parseRangeHeader(headers);
    parseOriginHeader(headers);
    boolean sendPartialResponse = true;
    boolean acceptRangeHeader = true;
    String contentType = "video/mp4";
    segmentStat.setSegmentRequestedAt(System.currentTimeMillis());
    // Delay x milly seconds serving of request
    Thread.sleep(networkRequestDelay);
    if (assetName.contains(".xml")) {
      contentType = "text/xml";
      sendPartialResponse = false;
    } else if (assetName.contains(".m3u8")) {
      contentType = "application/x-mpegURL";
      sendPartialResponse = false;
    } else if (assetName.contains(".ts")) {
      contentType = "video/mp2t";
      acceptRangeHeader = false;
    } else if (assetName.contains(".aac")) {
      contentType = "audio/aac";
    } else if (assetName.contains(".png")) {
      contentType = "image/png";
      sendPartialResponse = false;
    }
    openAssetFile(assetName);
    assetInput.reset();
    assetInput.skip(serveDataFromPosition);
    segmentStat.setSegmentFileName(assetName);
    segmentStat.setSegmentLengthInBytes(assetInput.available() - serveDataFromPosition);
    Log.i(TAG, "Serving file from position: " + serveDataFromPosition + ", remaining bytes: " +
        assetInput.available() + ", total file size: " + assetFileSize);
    if (serveDataFromPosition < assetFileSize) {
      if (sendPartialResponse) {
        sendHTTPOKPartialResponse(contentType, acceptRangeHeader);
        isPaused = false;
      } else {
        // Send complete response,, this is a short file
        sendHTTPOKCompleteResponse(contentType);
      }
    } else {
      sendRequestedRangeNotSatisfiable();
    }
  }

  public void jamNetwork(long jamPeriod, int jamFactor, boolean constantJam) {
    networkJammingEndPeriod = System.currentTimeMillis() + jamPeriod;
    this.networkJamFactor = jamFactor;
    this.constantJam = constantJam;
  }

  public void run() {
    isRunning = true;
    while (isRunning) {
      try {
        if (!isPaused) {
          // Serve static data only once !!!
          serveStaticData();
        } else {
          sleep(5);
        }
      } catch (InterruptedException e) {
        // Thread killed
      } catch (IOException e) {
        Log.e(TAG, "Connection closed by the client !!!", e);
        listener.segmentServed(requestUuid, segmentStat);
        isRunning = false;
      }
    }
  }

  private void openAssetFile(String assetFile) throws IOException {
    requestUuid = UUID.randomUUID().toString();
    if (assetInput != null) {
      try {
        assetInput.close();
      } catch (IOException e) {
        // Ignore
      }
    }
    try {
      AssetFileDescriptor fd = InstrumentationRegistry.getInstrumentation()
          .getContext().getAssets().openFd(assetFile);
      assetFileSize = fd.getLength();
      fd.close();
    } catch (IOException e) {
      // Can not determine file size
      assetFileSize = -1;
    }

    assetInput = InstrumentationRegistry.getInstrumentation()
        .getContext().getAssets().open(assetFile);
    assetInput.mark(1000000000);
    if (assetFileSize == -1) {
      assetFileSize = assetInput.available();
    }
  }

  /*
   * Send HTTP response 416 Requested range not satisfiable
   */
  private void sendRequestedRangeNotSatisfiable() throws IOException {
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(
        httpOut, StandardCharsets.US_ASCII), true);
    String response = "HTTP/1.1 416 Requested range not satisfiable\r\n" +
        "Server: SimpleHttpServer/1.0\r\n" +
        "Content-Range: bytes */" + assetFileSize + "\r\n" +
        "Content-Type: text/plain\r\n" +
        "Content-Length: 0\r\n" +
        SimpleHTTPServer.REQUEST_UUID_HEADER + ": " + requestUuid + "\r\n" +
        SimpleHTTPServer.REQUEST_NETWORK_DELAY_HEADER + ": " + networkRequestDelay + "\r\n" +
        getAdditionalHeadersAsString() +
        "Connection: close\r\n" +
        "\r\n";
    Log.i(TAG, "Sending response: \n" + response);
    writer.write(response);
    writer.flush();
    segmentStat.setSegmentRespondedAt(System.currentTimeMillis());
    listener.segmentServed(requestUuid, segmentStat);
  }

  public void sendHTTPOKCompleteResponse(String contentType) throws IOException {
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(
        httpOut, StandardCharsets.US_ASCII), true);
    String response = "HTTP/1.1 200 OK\r\n" +
        "Server: SimpleHttpServer/1.0\r\n" +
        "Content-Type: " + contentType + "; charset=utf-8" + "\r\n" +
        "Content-Length: " + assetFileSize + "\r\n" +
        getAdditionalHeadersAsString() +
        SimpleHTTPServer.REQUEST_UUID_HEADER + ": " + requestUuid + "\r\n" +
        SimpleHTTPServer.FILE_NAME_RESPONSE_HEADER + ": " + assetName + "\r\n" +
        SimpleHTTPServer.REQUEST_NETWORK_DELAY_HEADER + ": " + networkRequestDelay + "\r\n" +
        "Connection: keep-alive" + "\r\n" +
        "Accept-Ranges: bytes" + "\r\n" +
        (originHeaderValue.length() > 0 ?
            ("Access-Control-Allow-Origin: " + originHeaderValue + "\r\n" +
                "Access-Control-Allow-Credentials: true\r\n") : "") +
        "\r\n";
    Log.w(TAG, "Sending response: \n" + response);
    writer.write(response);
    writer.flush();
    int staticBuffSize = 200000;
    byte[] staticBuff = new byte[staticBuffSize];
    while (true) {
      int bytesRead = assetInput.read(staticBuff);
      String line = new String(staticBuff, 0, bytesRead, StandardCharsets.UTF_8);
      Log.w(TAG, line);
      writer.write(line);
      writer.flush();
      if (bytesRead < staticBuffSize) {
        break;
      }
    }
    writer.write("\r\n\r\n");
    writer.flush();
    segmentStat.setSegmentRespondedAt(System.currentTimeMillis());
    listener.segmentServed(requestUuid, segmentStat);
    segmentStat = new SegmentStatistics();
  }

  /*
   * Send HTTP response 206 partial content
   */
  public void sendHTTPOKPartialResponse(String contentType, boolean acceptRangeHeader)
      throws IOException {
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(
        httpOut, StandardCharsets.US_ASCII), true);
    String response = "HTTP/1.1 206 Partial Content\r\n" +
        "Server: SimpleHttpServer/1.0\r\n" +
        "Content-Type: " + contentType + "\r\n" +
        ("Content-Range: bytes " + this.serveDataFromPosition + "-" + (assetFileSize - 1)
            + "/" + assetFileSize + "\r\n") +
        (acceptRangeHeader ? ("Accept-Ranges: bytes" + "\r\n") : "") +
        // content length should be total length - requested byte position
        "Content-Length: " + (assetFileSize - this.serveDataFromPosition) + "\r\n" +
        SimpleHTTPServer.FILE_NAME_RESPONSE_HEADER + ": " + assetName + "\r\n" +
        SimpleHTTPServer.REQUEST_UUID_HEADER + ": " + requestUuid + "\r\n" +
        SimpleHTTPServer.REQUEST_NETWORK_DELAY_HEADER + ": " + networkRequestDelay + "\r\n" +
        getAdditionalHeadersAsString() +
        "Connection: close\r\n\r\n";

    Log.w(TAG, "Sending response: \n" + response);
    writer.write(response);
    writer.flush();
    segmentStat.setSegmentRespondedAt(System.currentTimeMillis());
  }

  private String getAdditionalHeadersAsString() {
    String additionalHeadersString = "";
    for (String headerName : additionalHeaders.keySet()) {
      additionalHeadersString += headerName + ": " + additionalHeaders.get(headerName) + "\r\n";
    }
    return additionalHeadersString;
  }

  /*
   * Write limited amount of bytes to httpOut each 100 ms
   */
  private void serveStaticData() throws IOException, InterruptedException {
    // TODO see how to implement seek latency correctly
//        if (!seekLatencyServed && serveDataFromPosition > 1000 &&
//                serveDataFromPosition < ((assetFileSize / 10) * 7)) {
//            Log.e("MuxStats", "Sleeping for: " + seekLatency);
//            Thread.sleep(seekLatency);
//            seekLatencyServed = true;
//        }
    int bytesToRead = transferBufferSize;
    if (networkJammingEndPeriod > System.currentTimeMillis()) {
      int jamFactor = this.networkJamFactor;
      if (!constantJam) {
        jamFactor = r.nextInt(this.networkJamFactor) + 2;
      }
      bytesToRead = (int) ((double) bytesToRead / (double) jamFactor);
    }
    segmentStat.setSegmentRespondedAt(System.currentTimeMillis());
    int bytesRead = assetInput.read(transferBuffer, 0, bytesToRead);
    if (bytesRead == -1) {
      // EOF reached
      Log.e(TAG, "EOF reached !!!");
      isRunning = false;
      segmentStat.setSegmentRespondedAt(System.currentTimeMillis());
      listener.segmentServed(requestUuid, segmentStat);
      segmentStat = new SegmentStatistics();
      return;
    }
    if (bytesRead > 0) {
      httpOut.write(transferBuffer, 0, bytesRead);
      sleep(10);
    }
  }
}
