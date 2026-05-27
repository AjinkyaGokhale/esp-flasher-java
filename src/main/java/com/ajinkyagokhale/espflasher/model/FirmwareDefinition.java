package com.ajinkyagokhale.espflasher.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FirmwareDefinition {

    private final String name;
    private final String description;
    private final String githubRepo;
    private final String websiteUrl;
    private final Map<String, String> chipBinMap;

    // constructor
    public FirmwareDefinition(String name, String description,
                              String githubRepo, String websiteUrl,
                              Map<String, String> chipBinMap) {
        this.name = name;
        this.description = description;
        this.githubRepo = githubRepo;
        this.websiteUrl = websiteUrl;
        this.chipBinMap = chipBinMap == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(chipBinMap));
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getGithubRepo() { return githubRepo; }
    public String getWebsiteUrl() { return websiteUrl; }
    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "chipBinMap is an unmodifiableMap wrapping a defensive copy; safe to expose")
    public Map<String, String> getChipBinMap() { return chipBinMap; }

    public String getBinForChip(String chip) {
        return chipBinMap.getOrDefault(chip, chipBinMap.get("default"));
    }

    @Override
    public String toString() { return name; }
}