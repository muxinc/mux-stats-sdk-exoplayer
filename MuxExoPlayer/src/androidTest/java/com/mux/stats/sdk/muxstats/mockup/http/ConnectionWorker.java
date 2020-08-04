package com.mux.stats.sdk.muxstats.mockup.http;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;


public class ConnectionWorker extends Thread {

    public static final String SERVE_VIDEO_DATA = "serve_video";

    Socket clientSocket;
    int bandwidthLimit;
    boolean isRunning;
    ConnectionReceiver receiver;
    ConnectionSender sender;

    public ConnectionWorker(Socket clientSocket, int bandwidthLimit) {
        this.clientSocket = clientSocket;
        this.bandwidthLimit = bandwidthLimit;
        start();
    }

    /*
     * Send HTTP response
     */
    public void sendHTTPOKResponse() throws IOException {
        OutputStream out = clientSocket.getOutputStream();
        PrintWriter writer = new PrintWriter(out);
        writer.write("HTTP/1.1 200 OK\n");
        writer.write("Server: SimpleHttpServer/1.0\n");
        writer.write("Content-Type: video/mp4\n");
        writer.write("Connection: keep-alive\n");
        writer.write("\n\n");
    }

    public void run() {
        isRunning = true;
        try {
            receiver = new ConnectionReceiver(clientSocket.getInputStream());
            receiver.start();
            sender = new ConnectionSender(clientSocket.getOutputStream());
            sender.start();
            sender.pause();
        } catch (IOException e) {
            e.printStackTrace();
            isRunning = false;
        }
        while (isRunning) {
            try {
                String action = receiver.getNextAction();
                sender.pause();
                if (action.equals(SERVE_VIDEO_DATA)) {
                    sendHTTPOKResponse();
                    sender.startServingFromPosition(0);
                }
            } catch (IOException e) {
                isRunning = false;
            }
        }
    }

    public void kill() {
        isRunning = false;
        interrupt();
        if (sender != null) {
            sender.kill();
        }
        if (receiver != null) {
            receiver.kill();
        }
    }
}
