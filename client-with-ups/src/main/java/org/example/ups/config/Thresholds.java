package org.example.ups.config;

public record Thresholds(double min, double max) {

    public Thresholds {
        if (min > max) {
            throw new IllegalArgumentException("min " + min + " is above max " + max);
        }
    }

    public boolean isOutOfRange(double value) {
        return value < min || value > max;
    }
}
