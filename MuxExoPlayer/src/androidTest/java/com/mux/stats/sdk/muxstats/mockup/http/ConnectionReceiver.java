package com.mux.stats.sdk.muxstats.mockup.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

import static com.mux.stats.sdk.muxstats.mockup.http.ConnectionWorker.SERVE_VIDEO_DATA;

public class ConnectionReceiver extends Thread {

    boolean isRunning;
    InputStream httpInput;
    HttpRequestParser httpParser = new HttpRequestParser();
    BufferedReader reader;
    LinkedBlockingDeque<String> actions = new LinkedBlockingDeque<>();

    public ConnectionReceiver(InputStream httpInput) {
        this.httpInput = httpInput;
        reader = new BufferedReader(new InputStreamReader(httpInput));
    }

    public void kill() {
        isRunning = false;
        interrupt();
    }

    public String getNextAction() {
        return actions.pop();
    }

    public void run() {
        isRunning = true;
        while (isRunning) {
            String httpRequest = waitForRequest();
            try {
                processRequest(httpRequest);
            } catch (IOException e) {
                // TODO handle this better
                e.printStackTrace();
            } catch (InterruptedException e) {
                // Someone killed the thread
            } catch (HttpFormatException e) {
                // TODO handle this better
                e.printStackTrace();
            }
        }
    }

    /*
     * Wait for http request
     */
    private String waitForRequest() {
        return reader.lines()
                .collect(Collectors.joining("\n"));
    }

    /*
     * Parse HTTP request
     */
    private void processRequest(String httpRequest) throws
            IOException, HttpFormatException, InterruptedException {
//            httpParser.parseRequest(httpRequest);
        // TODO parse the line at some point
        String requestLine = httpParser.getRequestLine();
        actions.put(SERVE_VIDEO_DATA);
    }
}
