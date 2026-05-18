package com.ajinkyagokhale.espflasher.model;

public record FlashConfig(
        String chip,
        int baudRate,
        String port,
        String binPath,
        String flashOffset,
        String esptoolCmd
) {


}

