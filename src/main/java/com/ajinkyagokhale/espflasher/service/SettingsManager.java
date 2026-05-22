package com.ajinkyagokhale.espflasher.service;

import com.ajinkyagokhale.espflasher.model.AppSettings;
import tools.jackson.databind.ObjectMapper;

import java.io.File;

public class SettingsManager {
    private static final String SETTINGS_DIR =
            System.getProperty("user.home") + "/.esp-flasher";

    private static final String SETTINGS_FILE =
            SETTINGS_DIR + "/settings.json";

    private final ObjectMapper mapper;
    private AppSettings settings;

    public SettingsManager() {
        mapper = new ObjectMapper();
        settings = new AppSettings();  // defaults
    }
    public void save() {
        try {
            // create directory if doesn't exist
            File dir = new File(SETTINGS_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(SETTINGS_FILE), settings);

        } catch (Exception e) {
            System.out.println("Failed to save settings: " + e.getMessage());
        }
    }

    public AppSettings load() {
        try {
            File file = new File(SETTINGS_FILE);
            if (file.exists()) {
                settings = mapper.readValue(file, AppSettings.class);
            }
        } catch (Exception e) {
            System.out.println("Failed to load settings: " + e.getMessage());
            settings = new AppSettings();  // fallback to defaults
        }
        return settings;
    }

    public AppSettings getSettings() {
        return settings;
    }

}
