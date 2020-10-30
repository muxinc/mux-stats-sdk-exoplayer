package com.mux.stats.sdk.muxstats.automatedtests.mockup.http;

import java.util.ArrayList;
import java.util.HashMap;

public class ServerAction {

    public static final int SERVE_MEDIA_DATA = 0;

    int type;
    HashMap<String, String> headers;
    String assetFile;

    public ServerAction(int type) {
        this.type = type;
    }

    public ServerAction(int type, HashMap<String, String> headers, String assetFile) {
        this.type = type;
        this.headers = headers;
        this.assetFile = assetFile;
    }

    public int getType() {
        return type;
    }

    public HashMap<String, String> getHeaders() {
        return headers;
    };

    public String getAssetFile() {
        return assetFile;
    }
}
