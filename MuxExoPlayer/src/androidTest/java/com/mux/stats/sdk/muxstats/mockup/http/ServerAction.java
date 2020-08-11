package com.mux.stats.sdk.muxstats.mockup.http;

public class ServerAction {

    public static final int SERVE_MEDIA_DATA = 0;

    int type;
    int value;

    public ServerAction(int type) {
        this.type = type;
    }

    public ServerAction(int type, int value) {
        this.type = type;
        this.value = value;
    }

    public int getType() {
        return type;
    }

    public int getValue() {
        return value;
    };
}
