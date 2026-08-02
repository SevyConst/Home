package org.example.server.exception;

public class DeviceChatsNotFoundException extends RuntimeException {
    public DeviceChatsNotFoundException(String message) {
        super(message);
    }
}
