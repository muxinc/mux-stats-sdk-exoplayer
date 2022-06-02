package com.mux.stats.sdk.muxstats.automatedtests.mockup;

import android.util.Log;

import com.mux.stats.sdk.muxstats.INetworkRequest;
import com.mux.stats.sdk.muxstats.MuxNetworkRequests;
import com.mux.stats.sdk.muxstats.automatedtests.BuildConfig;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MockNetworkRequest implements INetworkRequest {

  private static final String TAG = MockNetworkRequest.class.getSimpleName();
  public static final String EVENT_INDEX = "event_index";

  IMuxNetworkRequestsCompletion callback;
  ArrayList<JSONObject> receivedEvents = new ArrayList<>();
  MuxNetworkRequests muxNetwork;
  IMuxNetworkRequestsCompletion muxNetworkCallback = new IMuxNetworkRequestsCompletion() {

    @Override
    public void onComplete(boolean b) {
      // Do nothing
    }
  };


  public MockNetworkRequest() {
    muxNetwork = new MuxNetworkRequests();
  }

  public void sendResponse(boolean shouldSucceed) {
    callback.onComplete(shouldSucceed);
  }

  @Override
  public void get(URL url) {
    Log.v(TAG, "GET: " + url);
  }

  @Override
  public void post(URL url, JSONObject body, Hashtable<String, String> headers) {
    Log.v(TAG,"POST: " + url + ", body: " + body.toString());
    // TODO parse these requests to an events
  }

  @Override
  public void postWithCompletion(String domain, String envKey, String body,
      Hashtable<String, String> headers,
      IMuxNetworkRequestsCompletion callback) {
    try {
      JSONObject bodyJo = new JSONObject(body);
      JSONArray events = bodyJo.getJSONArray("events");
      for (int i = 0; i < events.length(); i++) {
        JSONObject eventJo = events.getJSONObject(i);
        receivedEvents.add(eventJo);
      }
      Log.v(TAG, "Mock network postWithCompletion called with body\n" + body);
      this.callback = callback;
      // For now always simulate a successful report
      callback.onComplete(true);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    if (BuildConfig.SHOULD_REPORT_INSTRUMENTATION_TEST_EVENTS_TO_SERVER) {
      // Send events to actual server
      muxNetwork.postWithCompletion(domain, envKey, body, headers, muxNetworkCallback);
    }
  }

  public String getReceivedEventName(int index) throws JSONException {
    if (index > receivedEvents.size()) {
      return null;
    }
    return receivedEvents.get(index).getString("e");
  }

  public JSONArray getReceivedEventsAsJSON() {
    JSONArray result = new JSONArray();
    for (JSONObject jo : receivedEvents) {
      result.put(jo);
    }
    return result;
  }

  public int getIndexForFirstEvent(String eventName) throws JSONException {
    for (int i = 0; i < receivedEvents.size(); i++) {
      if (getReceivedEventName(i).equals(eventName)) {
        return i;
      }
    }
    return -1;
  }

  public ArrayList<JSONObject> getAllEventsOfType(String eventType) throws JSONException {
    ArrayList<JSONObject> result = new ArrayList<>();
    for (int i = 0; i < receivedEvents.size(); i++) {
      JSONObject event = receivedEvents.get(i);
      if (getReceivedEventName(i).equalsIgnoreCase(eventType)) {
        event.put(EVENT_INDEX, i);
        result.add(event);
      }
    }
    return result;
  }

  public int getIndexForNextEvent(int startingIndex, String eventName) throws JSONException {
    for (int i = startingIndex; i < receivedEvents.size(); i++) {
      if (getReceivedEventName(i).equals(eventName)) {
        return i;
      }
    }
    return -1;
  }

  public int getIndexForLastEvent(String eventName) throws JSONException {
    for (int i = receivedEvents.size() - 1; i >= 0; i--) {
      if (getReceivedEventName(i).equals(eventName)) {
        return i;
      }
    }
    return -1;
  }

  public JSONObject getEventForIndex(int index) {
    if (index == -1 || index >= receivedEvents.size()) {
      return null;
    }
    return receivedEvents.get(index);
  }

  public ArrayList<String> getReceivedEventNames() throws JSONException {
    ArrayList<String> eventNames = new ArrayList<>();
    for (int i = 0; i < receivedEvents.size(); i++) {
      eventNames.add(getReceivedEventName(i));
    }
    return eventNames;
  }

  public int getNumberOfReceivedEvents() {
    return receivedEvents.size();
  }

  public long getCreationTimeForEvent(int index) throws JSONException {
    if (index > receivedEvents.size() || index < 0) {
      return -1;
    }
    return receivedEvents.get(index).getLong("uti");
  }
}
