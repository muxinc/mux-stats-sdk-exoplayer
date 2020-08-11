package com.mux.stats.sdk.muxstats.mockup.http;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;


import androidx.test.InstrumentationRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ConnectionSender extends Thread {

    static final String TAG = "HTTPTestConnSender";

    OutputStream httpOut;
    InputStream assetInput;
    Context context;
    boolean isPaused;
    boolean isRunning;

    long assetFileSize;
    long serveDataFromPosition;
    int bandwidthLimit;
    byte[] transferBuffer;
    int transferBufferSize;


    public ConnectionSender(OutputStream httpOut, int bandwidthLimit) throws IOException {
        this.httpOut = httpOut;
        this.bandwidthLimit = bandwidthLimit;

        AssetFileDescriptor fd = InstrumentationRegistry.getContext().getAssets().openFd("sample.mp4");
        assetFileSize = fd.getLength();
        fd.close();

        assetInput = InstrumentationRegistry.getContext().getAssets().open("sample.mp4");
        assetInput.mark(1000000000);

        transferBufferSize = bandwidthLimit / (8 * 100);
        transferBuffer = new byte[transferBufferSize]; // Max number of bytes to send each 10 ms
        isPaused = true;
        start();
    }

    public void kill() {
        isRunning = false;
        interrupt();
    }

    public void pause() {
        isPaused = true;
    }

    public void startServingFromPosition(long startAtByteNumber) throws IOException {
        this.serveDataFromPosition = startAtByteNumber;
        assetInput.reset();
        assetInput.skip(startAtByteNumber);
        Log.i(TAG, "Serving file from position: " + startAtByteNumber + ", remaining bytes: " +
                assetInput.available() + ", total file size: " + assetFileSize);
        if (serveDataFromPosition < assetFileSize) {
            sendHTTPOKResponse();
            isPaused = false;
        } else {
            sendRequestedRangeNotSatisfiable();
        }
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
                e.printStackTrace();
                Log.e(TAG, "Connection closed by the client !!!");
                isRunning = false;
            }
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
                "Content-Range: bytes */" + assetFileSize +
                "Content-Type: text/plain\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        Log.i(TAG, "Sending response: \n" + response);
        writer.write(response);
        writer.flush();
    }

    /*
     * Send HTTP response 206 partial content
     */
    public void sendHTTPOKResponse() throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                httpOut, StandardCharsets.US_ASCII), true);
        String response = "HTTP/1.1 206 Partial Content\r\n" +
                "Server: SimpleHttpServer/1.0\r\n" +
                "Content-Type: video/mp4\r\n" +
                ("Content-Range: bytes " + this.serveDataFromPosition + "-" + (assetFileSize-1)
                    + "/" + assetFileSize) +
                "Accept-Ranges: bytes\r\n" +
                // content length should be total length - requested byte position
                "Content-Length: " + (assetFileSize - this.serveDataFromPosition) + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        Log.w(TAG, "Sending response: \n" + response);
        writer.write(response);
        writer.flush();
    }

    /*
     * Write limited amount of bytes to httpOut each 100 ms
     */
    private void serveStaticData() throws IOException, InterruptedException {
        int bytesRead = assetInput.read(transferBuffer);
        if (bytesRead == -1) {
            // EOF reached
            Log.e(TAG, "EOF reached !!!");
            isRunning = false;
            return;
        }
        if (bytesRead > 0) {
            httpOut.write(transferBuffer, 0, bytesRead);
            sleep(10);
        }
    }
}
