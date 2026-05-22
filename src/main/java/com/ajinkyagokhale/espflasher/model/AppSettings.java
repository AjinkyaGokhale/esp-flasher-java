package com.ajinkyagokhale.espflasher.model;

public class AppSettings {

    private String lastPort;
    private String lastBinPath;
    private String lastChip;
    private String lastBaudRate;

    //logFile
    private String logFilePath;
    private boolean logFileEnabled;

    //constructor
    public AppSettings() {
        this.logFileEnabled = false;
        this.logFilePath = System.getProperty("user.home") + "/Documents/esp-flasher-logs";
        this.lastChip = "auto";
        this.lastBaudRate = "460800";
        this.lastBinPath = "";
        this.lastPort = "";
    }

    public String getLastPort() {
        return lastPort;
    }

    public void setLastPort(String lastPort) {
        this.lastPort = lastPort;
    }

    public String getLastBinPath() {
        return lastBinPath;
    }

    public void setLastBinPath(String lastBinPath) {
        this.lastBinPath = lastBinPath;
    }

    public String getLastChip() {
        return lastChip;
    }

    public void setLastChip(String lastChip) {
        this.lastChip = lastChip;
    }

    public String getLastBaudRate() {
        return lastBaudRate;
    }

    public void setLastBaudRate(String lastBaudRate) {
        this.lastBaudRate = lastBaudRate;
    }

    public String getLogFilePath() {
        return logFilePath;
    }

    public void setLogFilePath(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    public boolean isLogFileEnabled() {
        return logFileEnabled;
    }

    public void setLogFileEnabled(boolean logFileEnabled) {
        this.logFileEnabled = logFileEnabled;
    }
}