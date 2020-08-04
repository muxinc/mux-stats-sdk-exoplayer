package com.mux.stats.sdk.muxstats.mockup.http;

import android.content.res.AssetManager;

import androidx.test.InstrumentationRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConnectionSender extends Thread {

    OutputStream httpOut;
    int bandwidthLimit;
    byte[] assetBuffer;
    InputStream assetInput;
    boolean isPaused;
    boolean isRunning;
    long startAtMs;

    public ConnectionSender(OutputStream httpOut, int bandwidthLimit) throws IOException {
        this.httpOut = httpOut;
        this.bandwidthLimit = bandwidthLimit;
        assetInput = InstrumentationRegistry.getContext().getAssets().open("sample.mp4");
        assetBuffer = new byte[bandwidthLimit / 8 * 10]; // Max number of bytes to send each 100 ms
    }

    public void kill() {
        isRunning = false;
        interrupt();
    }

    public void pause() {
        isPaused = true;
    }

    public void startServingFromPosition(long startAtMs) {
        isPaused = false;
        this.startAtMs = startAtMs;
    }

    public void run() {
        while (isRunning) {
            try {
                if (!isPaused) {
                    serveStaticData();
                } else {
                    sleep(5);
                }
            } catch (InterruptedException e) {
                // Thread killed
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * Write limited amount of bytes to httpOut each 100 ms
     */
    private void serveStaticData() throws IOException, InterruptedException {
        int bytesRead = assetInput.read(assetBuffer);
        httpOut.write(assetBuffer, 0 , bytesRead);
        sleep(100);
    }
}
