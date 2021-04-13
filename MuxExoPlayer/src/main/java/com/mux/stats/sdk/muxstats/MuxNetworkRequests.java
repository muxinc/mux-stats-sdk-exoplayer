package com.mux.stats.sdk.muxstats;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import com.mux.stats.sdk.core.util.MuxLogger;
import com.mux.stats.sdk.muxstats.compat.AsyncTaskCompat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import org.json.JSONObject;

public class MuxNetworkRequests implements InetworkRequest {

  private static final String TAG = "MuxNetworkRequests";

  private interface NetworkRequest {

    URL getUrl();

    String getMethod();

    String getBody();

    Hashtable<String, String> getHeaders();
  }

  private static class GetRequest implements NetworkRequest {

    private final URL url;
    private final Hashtable<String, String> headers;

    GetRequest(URL url) {
      this.url = url;
      this.headers = new Hashtable<String, String>();
    }

    GetRequest(URL url, Hashtable<String, String> headers) {
      this.url = url;
      this.headers = headers == null ? new Hashtable<String, String>() : headers;
    }

    @Override
    public URL getUrl() {
      return url;
    }

    @Override
    public String getMethod() {
      return "GET";
    }

    @Override
    public String getBody() {
      return null;
    }

    @Override
    public Hashtable<String, String> getHeaders() {
      return headers;
    }
  }

  private static class PostRequest implements NetworkRequest {

    private final URL url;
    private final String body;
    private final Hashtable<String, String> headers;

    PostRequest(URL url, String body) {
      this.url = url;
      this.body = body == null ? "" : body;
      this.headers = new Hashtable<String, String>();
    }

    PostRequest(URL url, String body, Hashtable<String, String> headers) {
      this.url = url;
      this.body = body == null ? "" : body;
      this.headers = headers == null ? new Hashtable<String, String>() : headers;
    }

    @Override
    public URL getUrl() {
      return url;
    }

    @Override
    public String getMethod() {
      return "POST";
    }

    @Override
    public String getBody() {
      return body;
    }

    @Override
    public Hashtable<String, String> getHeaders() {
      return headers;
    }
  }

  private static class NetworkTaskRunner extends AsyncTask<NetworkRequest, Void, Void> {

    private static final int READ_TIMEOUT_MS = 20 * 1000;
    private static final int CONNECT_TIMEOUT_MS = 30 * 1000;
    private static final int MAXIMUM_RETRY = 4;
    private static final int BASE_TIME_BETWEEN_BEACONS = 5000;
    private final ImuxNetworkRequestsCompletion callback;
    private int failureCount = 0;

    public NetworkTaskRunner(ImuxNetworkRequestsCompletion callback) {
      this.callback = callback;
    }

    private long getNextBeaconTime() {
      if (failureCount == 0) {
        return 0;
      }
      double factor = Math.pow(2, failureCount - 1);
      factor = factor * Math.random();
      return (long) (1 + factor) * BASE_TIME_BETWEEN_BEACONS;
    }

    @Override
    protected Void doInBackground(NetworkRequest... params) {
      NetworkRequest request = params[0];
      URL url = request.getUrl();
      String method = request.getMethod();
      Hashtable<String, String> headers = request.getHeaders();
      String body = request.getBody();

      MuxLogger.debug(TAG, "making " + method + " request to: " + url.toString());
      boolean successful = false;
      while (!successful && failureCount < MAXIMUM_RETRY) {
        try {
          Thread.sleep(getNextBeaconTime());
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        successful = executeHttp(url, method, headers, body);
      }
      if (callback != null) {
        callback.onComplete(successful);
      }
      return null;
    }

    private boolean executeHttp(URL url, String method, Hashtable<String, String> headers,
        String body) {
      HttpURLConnection conn = null;
      InputStream stream = null;
      boolean successful = true;

      try {
        conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setRequestMethod(method);

        // Load in the headers passed in the request
        Enumeration<String> headerKeys = headers.keys();
        boolean shouldGzip = false;
        while (headerKeys.hasMoreElements()) {
          String key = headerKeys.nextElement();
          String value = headers.get(key);
          conn.setRequestProperty(key, value);
          if (key.equalsIgnoreCase("Content-Encoding") && value.equalsIgnoreCase("gzip")) {
            shouldGzip = true;
          }
        }

        // Handle the case where we have a POST and need to put the body in
        if (method.equals("POST")) {
          conn.setRequestProperty("Content-Type", "application/json");
          byte[] bytes = body.getBytes();
          if (shouldGzip) {
            MuxLogger.debug(TAG, "gzipping");
            bytes = gzip(bytes);
          }

          OutputStream outputStream = conn.getOutputStream();
          outputStream.write(bytes);
          outputStream.close();
        }

        conn.connect();
        stream = conn.getInputStream();
        MuxLogger.debug(TAG, "got response: " + conn.getResponseCode());
      } catch (Exception e) {
        Log.e(TAG, e.getMessage(), e);
        successful = false;
        failureCount++;
      } finally {
        if (stream != null) {
          try {
            stream.close();
          } catch (IOException ioe) {
            Log.e(TAG, ioe.getMessage(), ioe);
            successful = false;
            failureCount++;
          }
        }
        if (conn != null) {
          conn.disconnect();
        }
      }
      return successful;
    }

    private static byte[] gzip(byte[] input) throws Exception {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
      gzipOutputStream.write(input);
      gzipOutputStream.flush();
      gzipOutputStream.close();
      return byteArrayOutputStream.toByteArray();
    }
  }

  private String getAuthority(String propertykey) {
    if (Pattern.matches("^[a-z0-9]+$", propertykey)) {
      return propertykey + ".litix.io";
    }
    return "img.litix.io";
  }


  @Override
  public void get(URL url) {
    try {
      AsyncTaskCompat.executeParallel(new NetworkTaskRunner(null), new GetRequest(url));
    } catch (Exception e) {
      Log.e(TAG, e.getMessage(), e);
    }
  }

  @Override
  public void post(URL url, JSONObject body, Hashtable<String, String> headers) {
    try {
      AsyncTaskCompat.executeParallel(new NetworkTaskRunner(null),
          new PostRequest(url, body.toString(), headers));
    } catch (Exception e) {
      Log.e(TAG, e.getMessage(), e);
    }
  }

  @Override
  public void postWithCompletion(String propertyKey, String body,
      Hashtable<String, String> headers,
      InetworkRequest.ImuxNetworkRequestsCompletion callback) {
    try {
      if (propertyKey != null) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme("https").authority(this.getAuthority(propertyKey)).path(
            "android");
        AsyncTaskCompat.executeParallel(new NetworkTaskRunner(callback),
            new PostRequest(new URL(uriBuilder.build().toString()), body, headers));
      } else {
        throw new Exception("propertyKey is null");
      }
    } catch (Exception e) {
      Log.e(TAG, e.getMessage(), e);
      callback.onComplete(true);
    }
  }
}
