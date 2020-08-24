package com.mux.stats.sdk.muxstats.automatedtests.mockup;

public class Event {

    String name;
    long expectedTime;

    public Event(String name) {
        this.name = name;
        this.expectedTime = System.currentTimeMillis();
    }

    public Event(String name, long expectedTime) {
        this.name = name;
        this.expectedTime = expectedTime;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getExpectedTime() {
        return expectedTime;
    }

    public void setExpectedTime(long expectedTime) {
        this.expectedTime = expectedTime;
    }
}
