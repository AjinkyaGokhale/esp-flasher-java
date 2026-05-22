package com.ajinkyagokhale.espflasher.model;

public class FlashResult {
    private boolean success;
    private String message;
    private String macAddress;
    private String port;
    private String timeStamp;

    public FlashResult(boolean success, String message, String macAddress, String port, String timeStamp) {
        this.success = success;
        this.message = message;
        this.macAddress = macAddress;
        this.port = port;
        this.timeStamp = timeStamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getMacAddress() {
        return macAddress;
    }
    public String getPort() {
        return port;
    }
    public String getTimeStamp() {
        return timeStamp;
    }
}
