package com.ajinkyagokhale.espflasher.listener;

public interface FlashListener {

    void onProgress(int percent);
    void onLog(String line);
    void onComplete(boolean success, String message);
}



