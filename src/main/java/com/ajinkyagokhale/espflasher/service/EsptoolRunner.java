package com.ajinkyagokhale.espflasher.service;

import com.ajinkyagokhale.espflasher.listener.FlashListener;
import com.ajinkyagokhale.espflasher.model.FlashConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EsptoolRunner {
    private volatile Process currentProcess;
    private volatile boolean isCancelled;

    public void startFlashing(FlashConfig config, FlashListener listener) {

        isCancelled = false;

        Thread thread = new Thread(() -> {

            List<String> command = new ArrayList<>(tokenize(config.esptoolCmd()));
            command.add("--chip");
            command.add(config.chip());
            command.add("--port");
            command.add(config.port());
            command.add("--baud");
            command.add(String.valueOf(config.baudRate()));
            command.add("write-flash");
            command.add(config.flashOffset());
            command.add(config.binPath());

            //start the process
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                currentProcess = pb.start();

                Pattern pattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (isCancelled) break;
                        listener.onLog(line);
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            int percent = (int) Double.parseDouble(matcher.group(1));
                            listener.onProgress(percent);
                        }
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

    // Tokenize a command string, respecting double-quoted segments so paths with spaces work.
    static List<String> tokenize(String cmd) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < cmd.length(); i++) {
            char c = cmd.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (!cur.isEmpty()) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }
}

