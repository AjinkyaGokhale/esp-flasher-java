package com.ajinkyagokhale.espflasher.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {

    private final UpdateService service = new UpdateService();

    @Test
    void detectsNewerPatch() {
        assertTrue(service.isNewer("1.0.2", "1.0.1"));
    }

    @Test
    void detectsNewerWithVPrefix() {
        assertTrue(service.isNewer("v1.0.2", "1.0.1"));
    }

    @Test
    void equalVersionsAreNotNewer() {
        assertFalse(service.isNewer("v1.0.1", "1.0.1"));
    }

    @Test
    void olderVersionIsNotNewer() {
        assertFalse(service.isNewer("1.0.0", "1.0.1"));
    }

    @Test
    void comparesNumericallyNotLexically() {
        assertTrue(service.isNewer("1.1.0", "1.0.9"));
        assertTrue(service.isNewer("1.10.0", "1.9.0"));
    }

    @Test
    void handlesShorterVersions() {
        assertTrue(service.isNewer("2.0", "1.9.9"));
        assertFalse(service.isNewer("1.0", "1.0.0"));
    }

    @Test
    void malformedInputIsNotNewer() {
        assertFalse(service.isNewer("garbage", "1.0.1"));
    }
}
