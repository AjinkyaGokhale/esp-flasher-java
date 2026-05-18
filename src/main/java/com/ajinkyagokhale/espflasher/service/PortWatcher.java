package com.ajinkyagokhale.espflasher.service;

import com.ajinkyagokhale.espflasher.listener.PortListener;
import com.fazecast.jSerialComm.SerialPort;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PortWatcher {

    private Set<String> previousPorts;
    private boolean isWatching;
    private ScheduledExecutorService scheduler;


    public void startWatching(PortListener listener) {
        isWatching = true;
        previousPorts = getCurrentPorts();

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (!isWatching) return;

            Set<String> currentPorts = getCurrentPorts();

            for (String port : currentPorts) {
                if (!previousPorts.contains(port)) {
                    // new port found!
                    try {
                        Thread.sleep(2000);  // wait 2 seconds for OS to initialise port
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    listener.onNewPort(port);
                }
            }

            previousPorts = currentPorts;

        }, 0, 1, TimeUnit.SECONDS);
    }

    public void stopWatching() {
        isWatching = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

    }

    //helpers
    private Set<String> getCurrentPorts() {
        Set<String> ports = new HashSet<>();
        for (SerialPort port : SerialPort.getCommPorts()) {
            ports.add(port.getSystemPortName());  // "COM3" or "ttyUSB0"
        }
        return ports;
    }
}
