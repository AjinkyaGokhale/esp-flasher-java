package com.ajinkyagokhale.espflasher.service;

import com.ajinkyagokhale.espflasher.model.FirmwareDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FirmwareCatalog {

    public static List<FirmwareDefinition> getCatalog() {
        return List.of(
                new FirmwareDefinition(
                        "Tasmota",
                        "Universal smart home firmware with web UI and MQTT",
                        "arendst/Tasmota",
                        "https://tasmota.github.io",
                        tasmotaBins()
                ),
                new FirmwareDefinition(
                        "Tasmota SML (ottelo9)",
                        "Tasmota with SML support for smart meter reading",
                        "ottelo9/tasmota-sml-images",
                        "https://github.com/ottelo9/tasmota-sml-images",
                        tasmotaSmlBins()
                )
        );
    }

    private static Map<String, String> tasmotaBins() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("esp32c6", "tasmota32c6.bin");
        m.put("esp32",   "tasmota32.bin");
        m.put("esp32s2", "tasmota32s2.bin");
        m.put("esp32s3", "tasmota32s3.bin");
        m.put("esp32c3", "tasmota32c3.bin");
        m.put("esp8266", "tasmota.bin");
        m.put("default", "tasmota32.bin");
        return m;
    }

    private static Map<String, String> tasmotaSmlBins() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("esp32c6", "tasmota32c6_ottelo_tas.zip");
        m.put("esp32",   "tasmota32_ottelo_tas.zip");
        m.put("esp32s2", "tasmota32s2_ottelo_tas.zip");
        m.put("esp32s3", "tasmota32s3_ottelo_tas.zip");
        m.put("esp32c3", "tasmota32c3_ottelo_tas.zip");
        m.put("esp8266", "tasmota8266_bundle_ottelo.zip");
        m.put("default", "tasmota32_ottelo_tas.zip");
        return m;
    }

}
