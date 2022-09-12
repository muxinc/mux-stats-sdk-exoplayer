package com.mux.stats.sdk.muxstats;

import android.net.Uri;
import android.os.AsyncTask;
import com.mux.stats.sdk.core.util.MuxLogger;
import com.mux.stats.sdk.muxstats.INetworkRequest;
import com.mux.stats.sdk.muxstats.MuxStats;
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

/**
 * Network communication wrapper, instance of this class is used to handle all network communication
 * for all {@link MuxStatsExoPlayer} objects in this process. Instance of this class is associated
 * with {@link MuxStats} object as a static variable set by {@link MuxStats#setHostNetworkApi}
 * method, in this way it is only possible to have single MuxNetworkRequests instance at a time.
 */
public class MuxNetworkRequests implements INetworkRequest {

  private static final String TAG = "MuxNetworkRequests";

  /**
   * This interface defines communication methods with the backend.
   */
  private interface NetworkRequest {
    /** Return the url of the backend server. */
    URL getUrl();

    /** Return the name of HTTP method used to send request to the server. */
    String getMethod();

    /** Return the request body, only use in case of POST request. */
    String getBody();

    /** Return the HTTP headers associated with this request. */
    Hashtable<String, String> getHeaders();
  }

  /**
   * HTTP GET method implementation of @link {@link NetworkRequest} interface.
   */
  private static class GetRequest implements NetworkRequest {
    /** Backend server url. */
    private final URL url;
    /** Backend server url. */
    private final Hashtable<String, String> headers;

    /**
     * Basic constructor.
     *
     * @param url to send GET request to.
     */
    GetRequest(URL url) {
      this.url = url;
      this.headers = new Hashtable<String, String>();
    }

    /**
     * Constructor with extra HTTP headers associated with the GET request.
     *
     * @param url to send request to.
     * @param headers to send with this request.
     */
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

  /**
   * HTTP POST method implementation of @link {@link NetworkRequest} interface.
   */
  private static class PostRequest implements NetworkRequest {
    /** URL to send request to. */
    private final URL url;
    /** POST method body to send with this request. */
    private final String body;
    /** HTTP headers to send with this POST request. */
    private final Hashtable<String, String> headers;

    /**
     * Basic constructor with body.
     *
     * @param url to send request to.
     * @param body payload to send with this request.
     */
    PostRequest(URL url, String body) {
      this.url = url;
      this.body = body == null ? "" : body;
      this.headers = new Hashtable<String, String>();
    }

    /**
     * Constructor with additional HTTP headers.
     *
     * @param url to send request to.
     * @param body payload to send with this request.
     * @param headers to send with this request.
     */
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

  /**
   * This is the asynchronous implementation of network dispatcher, runs on main Thread but does not
   * block it. Expose methods for:
   * <ul>
   *   <li>Get request: @link {@link NetworkRequest#get(URL)}</li>
   *   <li>Post request: @link {@link NetworkRequest#post(URL, JSONObject, Hashtable)}</li>
   *   <li>Post request with callback to be called on completion: @link {@link
   *   NetworkRequest#postWithCompletion(String, String, Hashtable, IMuxNetworkRequestsCompletion)}
   *   </li>
   * </ul>
   */
  private static class NetworkTaskRunner extends AsyncTask<NetworkRequest, Void, Void> {
    /** Time limit to wait for server to respond on request. */
    private static final int READ_TIMEOUT_MS = 20 * 1000;
    /** Kill connection if stale for this number of milliseconds. */
    private static final int CONNECT_TIMEOUT_MS = 30 * 1000;
    /** Number of times to try to send request to backend, if server can not be reached. */
    private static final int MAXIMUM_RETRY = 4;
    /**
     * Time to wait before attempting to resend failed request, this value is used as a base for
     * exponential calculation on each failed attempt.
     */
    private static final int BASE_TIME_BETWEEN_BEACONS = 5000;
    /** Callback to be executed after each successful request. */
    private final IMuxNetworkRequestsCompletion callback;
    /** Number of failed attempts on network request. */
    private int failureCount = 0;

    /**
     * Basic constructor.
     *
     * @param callback to be called when request is completed.
     */
    public NetworkTaskRunner(IMuxNetworkRequestsCompletion callback) {
      this.callback = callback;
    }

    /**
     * Calculate time to wait for each failed post request, time to wait increase exponentially on
     * each failed request.
     *
     * @return number of milliseconds to wait until sending next HTTP request.
     */
    private long getNextBeaconTime() {
      if (failureCount == 0) {
        return 0;
      }
      double factor = Math.pow(2, failureCount - 1);
      factor = factor * Math.random();
      return (long) (1 + factor) * BASE_TIME_BETWEEN_BEACONS;
    }

    /**
     * Network communication wrapper. this function make sure to resend the same request appropriate
     * number of times in case server is unreachable.
     * This method is executed asynchronously on Main thread.
     *
     * @param params HTTP request to be executed.
     */
    @Override
    protected Void doInBackground(NetworkRequest... params) {
      NetworkRequest request = params[0];
      URL url = request.getUrl();
      String method = request.getMethod();
      Hashtable<String, String> headers = request.getHeaders();
      String body = request.getBody();

      MuxLogger.d(TAG, "making " + method + " request to: " + url.toString());
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

    /**
     * Actual HTTP communication implementation.
     *
     * @param url to send request to.
     * @param method method to use (POST or GET).
     * @param headers to send with request.
     * @param body payload to send with the request.
     */
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
          if (key.equalsIgnoreCase("Content-Encoding")
              && value.equalsIgnoreCase("gzip")) {
            shouldGzip = true;
          }
        }

        // Handle the case where we have a POST and need to put the body in
        if (method.equals("POST")) {
          conn.setRequestProperty("Content-Type", "application/json");
          byte[] bytes = body.getBytes();
          if (shouldGzip) {
            MuxLogger.d(TAG, "gzipping");
            bytes = gzip(bytes);
          }

          OutputStream outputStream = conn.getOutputStream();
          outputStream.write(bytes);
          outputStream.close();
        }

        conn.connect();
        stream = conn.getInputStream();
        MuxLogger.d(TAG, "got response: " + conn.getResponseCode());
      } catch (Exception e) {
        MuxLogger.d(TAG, e.getMessage());
        successful = false;
        failureCount++;
      } finally {
        if (stream != null) {
          try {
            stream.close();
          } catch (IOException ioe) {
            MuxLogger.d(TAG, ioe.getMessage());
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

    /**
     * Apply gzip algorithm to a byte array and return gziped byte array.
     *
     * @param input bytes to perform gzip on.
     * @return gziped byte array.
     * @throws Exception
     */
    private static byte[] gzip(byte[] input) throws Exception {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
      gzipOutputStream.write(input);
      gzipOutputStream.flush();
      gzipOutputStream.close();
      return byteArrayOutputStream.toByteArray();
    }
  }

  /**
   * Calculate the backend URL based on the user environment key variable.
   *
   * @param propertykey environment key variable.
   * @param domain domain to send beacons to (concatenated with propertykey).
   * @return backend url.
   */
  private String getAuthority(String propertykey, String domain) {
    if (Pattern.matches("^[a-z0-9]+$", propertykey)) {
        return propertykey + domain;
    }
    return "img" + domain;
  }

  /**
   * Dispatch the GET request to a URL asynchronously.
   *
   * @param url to send request to.
   */
  @Override
  public void get(URL url) {
    try {
      AsyncTaskCompat.executeParallel(new NetworkTaskRunner(null), new GetRequest(url));
    } catch (Exception e) {
      MuxLogger.d(TAG, e.getMessage());
    }
  }

  /**
   * Send post request to a URL asynchronously.
   *
   * @param url to send request to.
   * @param body to attach to the request.
   * @param headers to send with the request.
   */
  @Override
  public void post(URL url, JSONObject body, Hashtable<String, String> headers) {
    try {
      AsyncTaskCompat.executeParallel(new NetworkTaskRunner(null),
          new PostRequest(url, body.toString(), headers));
    } catch (Exception e) {
      MuxLogger.d(TAG, e.getMessage());
    }
  }

  /**
   * Send HTTP request to the backend and execute given callback if completed successfully.
   *
   * @param propertyKey environment key that associate the user with the backend, this value is
   *                    used to determine the backend URL.
   * @param body payload to send with request.
   * @param headers to send with the request.
   * @param callback to execute on successful completion.
   */
  @Override
  public void postWithCompletion(String domain, String propertyKey, String body,
      Hashtable<String, String> headers,
      INetworkRequest.IMuxNetworkRequestsCompletion callback) {
    try {
      if (propertyKey != null) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme("https").authority(this.getAuthority(propertyKey, domain)).path(
            "android");
        AsyncTaskCompat.executeParallel(new NetworkTaskRunner(callback),
            new PostRequest(new URL(uriBuilder.build().toString()), body, headers));
      } else {
        throw new Exception("propertyKey is null");
      }
    } catch (Exception e) {
      MuxLogger.d(TAG, e.getMessage());
      callback.onComplete(true);
    }
  }
}
