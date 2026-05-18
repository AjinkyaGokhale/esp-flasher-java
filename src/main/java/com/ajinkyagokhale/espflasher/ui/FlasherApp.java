package com.ajinkyagokhale.espflasher.ui;

import com.ajinkyagokhale.espflasher.listener.FlashListener;
import com.ajinkyagokhale.espflasher.listener.PortListener;
import javafx.application.Application;
import javafx.stage.Stage;

public class FlasherApp extends Application implements FlashListener, PortListener {

    @Override
    public void onProgress(int percent) {

    }

    @Override
    public void onLog(String line) {

    }

    @Override
    public void onComplete(boolean success, String message) {

    }

    @Override
    public void onNewPort(String portName) {

    }

    @Override
    public void start(Stage primaryStage) throws Exception {

    }
}
