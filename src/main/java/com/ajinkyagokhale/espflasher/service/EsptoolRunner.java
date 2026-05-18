package com.ajinkyagokhale.espflasher.service;

import com.ajinkyagokhale.espflasher.listener.FlashListener;
import com.ajinkyagokhale.espflasher.model.FlashConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EsptoolRunner {
    private Process currentProcess;
    private boolean isCancelled;

    public void startFlashing(FlashConfig config, FlashListener listener) {

        isCancelled = false;

        Thread thread = new Thread(() -> {
            List<String> command = new ArrayList<>();

            for (String part : config.esptoolCmd().split(" ")) {
                command.add(part);
            }
            command.add("--chip");
            command.add(config.chip());
            command.add("--port");
            command.add(config.port());
            command.add("--baud");
            command.add(String.valueOf(config.baudRate()));
            command.add("write_flash");
            command.add(config.flashOffset());
            command.add(config.binPath());

            //start the process
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                currentProcess = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()));
                String line;
                Pattern pattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
                while ((line = reader.readLine()) != null) {
                    if (isCancelled) break;
                    listener.onLog(line);
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        int percent = (int) Double.parseDouble(matcher.group(1));
                        listener.onProgress(percent);
                    }
                }
                int exitCode = currentProcess.waitFor();

                if (isCancelled) {
                    listener.onComplete(false, "Cancelled by user.");
                } else if (exitCode == 0) {
                    listener.onProgress(100);
                    listener.onComplete(true, "Flash successful!");
                } else {
                    listener.onComplete(false, "esptool failed with exit code: " + exitCode);
                }

            } catch (IOException | InterruptedException e) {
                listener.onComplete(false, "Error: " + e.getMessage());
                return;
            }

        });
        thread.setDaemon(true);
        thread.start();


    }

    public void stopFlashing() {
        isCancelled = true;
        if (currentProcess != null) {
            currentProcess.destroyForcibly();
        }
    }

}

