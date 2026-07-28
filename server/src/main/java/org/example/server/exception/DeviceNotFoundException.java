package org.example.server.exception;

public class DeviceNotFoundException extends RuntimeException {
    public DeviceNotFoundException(String deviceId) {
        super("device_id '" + deviceId + "' is absent in the db");
    }
}
