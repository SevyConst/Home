package org.example.server.exception;

public class DeviceInfoIsEmpty extends RuntimeException {
    public DeviceInfoIsEmpty(String message) {
        super(message);
    }
}
