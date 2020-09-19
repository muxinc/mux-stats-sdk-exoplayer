package com.mux.stats.sdk.muxstats.automatedtests.mockup.http;

public class ServerAction {

    public static final int SERVE_MEDIA_DATA = 0;

    int type;
    int value;
    String assetFile;

    public ServerAction(int type) {
        this.type = type;
    }

    public ServerAction(int type, int value, String assetFile) {
        this.type = type;
        this.value = value;
        this.assetFile = assetFile;
    }

    public int getType() {
        return type;
    }

    public int getValue() {
        return value;
    };

    public String getAssetFile() {
        return assetFile;
    }
}
