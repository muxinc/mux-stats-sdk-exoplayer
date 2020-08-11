package com.mux.stats.sdk.muxstats.mockup.http;

import android.content.Context;

import java.io.IOException;
import java.net.Socket;


public class ConnectionWorker extends Thread {

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

    public void run() {
        isRunning = true;
        try {
            receiver = new ConnectionReceiver(clientSocket.getInputStream());
            receiver.start();
            sender = new ConnectionSender(clientSocket.getOutputStream(), bandwidthLimit);
            sender.pause();
        } catch (IOException e) {
            e.printStackTrace();
            isRunning = false;
        }
        while (isRunning) {
            try {
                ServerAction action = receiver.getNextAction();
                sender.pause();
                if (action.getType() == ServerAction.SERVE_MEDIA_DATA) {
                    sender.startServingFromPosition(action.getValue());
                }
            } catch (IOException e) {
                isRunning = false;
            } catch (InterruptedException e) {
                // Someone killed the thread
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
