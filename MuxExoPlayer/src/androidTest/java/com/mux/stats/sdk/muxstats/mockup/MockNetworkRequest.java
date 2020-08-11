package com.mux.stats.sdk.muxstats.mockup;

import com.mux.stats.sdk.core.model.ViewData;
import com.mux.stats.sdk.muxstats.INetworkRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;

public class MockNetworkRequest implements INetworkRequest {
    IMuxNetworkRequestsCompletion callback;
    ArrayList<JSONObject> receivedEvents = new ArrayList<>();

    public void sendResponse(boolean shouldSucceed) {
        callback.onComplete(shouldSucceed);
    }

    @Override
    public void get(URL url) {
        System.out.println("GET: " + url);
    }

    @Override
    public void post(URL url, JSONObject body, Hashtable<String, String> headers) {
        System.out.println("POST: " + url + ", body: " + body.toString());
        // TODO parse these requests to an events
    }

    @Override
    public void postWithCompletion(String envKey, String body,
                                   Hashtable<String, String> headers,
                                   IMuxNetworkRequestsCompletion callback) {
        try {
            JSONObject bodyJo = new JSONObject(body);
            JSONArray events = bodyJo.getJSONArray("events");
            for (int i = 0; i < events.length(); i++) {
                JSONObject eventJo = events.getJSONObject(i);
                receivedEvents.add(eventJo);
            }
            System.out.println("Mock network postWithCompletion called !!!");
            this.callback = callback;
            // For now always simulate a successful report
            callback.onComplete(true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getReceivedEventName(int index) throws JSONException {
        if (index > receivedEvents.size()) {
            return null;
        }
        return receivedEvents.get(index).getString("e");
    }

    public ArrayList<String> getReceivedEventNames() throws JSONException {
        ArrayList<String> eventNames = new ArrayList<>();
        for (int i =0; i < receivedEvents.size(); i++) {
            eventNames.add(getReceivedEventName(i));
        }
        return eventNames;
    }

    public int getNumberOfReceivedEvents() {
        return receivedEvents.size();
    }

    public long getTimeToFirstFrameForEvent(int index) throws JSONException {
        JSONObject event = receivedEvents.get(index);
        return  event.has(ViewData.VIEW_TIME_TO_FIRST_FRAME) ?
                event.getLong(ViewData.VIEW_TIME_TO_FIRST_FRAME) : -1;
    }
}
