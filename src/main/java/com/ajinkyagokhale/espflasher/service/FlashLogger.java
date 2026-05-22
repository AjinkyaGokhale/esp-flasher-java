package com.ajinkyagokhale.espflasher.service;

import com.ajinkyagokhale.espflasher.model.AppSettings;
import com.ajinkyagokhale.espflasher.model.FlashResult;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class FlashLogger {

    private static final String HEADER = "timestamp,port,mac_address,status,message";

    private final AppSettings settings;

    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public FlashLogger(AppSettings settings) {
        this.settings = settings;
    }

    public void append(FlashResult result) {
        if (!settings.isLogFileEnabled()) return;

        File dir = new File(settings.getLogFilePath());
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Failed to create log directory: " + dir);
            return;
        }

        File logFile = new File(dir, "flash-log.csv");
        boolean writeHeader = !logFile.exists() || logFile.length() == 0;

        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, StandardCharsets.UTF_8, true))) {
            if (writeHeader) pw.println(HEADER);
            pw.printf("%s,%s,%s,%s,\"%s\"%n",
                    result.getTimeStamp(),
                    result.getPort(),
                    result.getMacAddress() != null ? result.getMacAddress() : "",
                    result.isSuccess() ? "success" : "failed",
                    result.getMessage().replace("\"", "\"\""));
        } catch (IOException e) {
            System.err.println("Failed to write flash log: " + e.getMessage());
        }
    }
}
