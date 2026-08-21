package org.example.ups.config;

import lombok.Builder;

import java.net.URI;
import java.time.Duration;

@Builder
public record UpsClientConfig(
        URI serverUri,
        Duration serverConnectTimeout,
        Duration serverRequestTimeout,
        String deviceId,
        String nutHost,
        int nutPort,
        String nutUpsName,
        int nutConnectTimeoutMilliseconds,
        int nutReadTimeoutMilliseconds,
        Duration upsPollPeriod,
        URI haWebhookUri,
        Duration haConnectTimeout,
        Duration haRequestTimeout,
        int haInitBackoffMilliseconds,
        Duration haHeartbeatPeriod,
        Thresholds inputVoltage,
        Thresholds outputVoltage,
        Thresholds frequency,
        Thresholds load,
        Thresholds battery
) {

    public static UpsClientConfig readEnv() {
        return UpsClientConfig.builder()
                .serverUri(requiredHttpUri("SERVER_URL"))
                .serverConnectTimeout(requiredPositiveSeconds("SERVER_CONNECT_TIMEOUT_SECONDS"))
                .serverRequestTimeout(requiredPositiveSeconds("SERVER_REQUEST_TIMEOUT_SECONDS"))
                .deviceId(required("DEVICE_ID"))
                .nutHost(required("NUT_HOST"))
                .nutPort(requiredPort("NUT_PORT"))
                .nutUpsName(required("NUT_UPS_NAME"))
                .nutConnectTimeoutMilliseconds(requiredPositiveInt("NUT_CONNECT_TIMEOUT_MILLISECONDS"))
                .nutReadTimeoutMilliseconds(requiredPositiveInt("NUT_READ_TIMEOUT_MILLISECONDS"))
                .upsPollPeriod(requiredPositiveMilliseconds("UPS_POLL_PERIOD_MILLISECONDS"))
                .haWebhookUri(requiredHttpUri("HA_WEBHOOK_URL"))
                .haConnectTimeout(requiredPositiveSeconds("HA_CONNECT_TIMEOUT_SECONDS"))
                .haRequestTimeout(requiredPositiveSeconds("HA_REQUEST_TIMEOUT_SECONDS"))
                .haInitBackoffMilliseconds(requiredPositiveInt("HA_BACKOFF_MILLISECONDS"))
                .haHeartbeatPeriod(requiredPositiveSeconds("HA_HEARTBEAT_PERIOD_SECONDS"))
                .inputVoltage(
                        new Thresholds(
                                requiredDouble("INPUT_VOLTAGE_MIN"),
                                requiredDouble("INPUT_VOLTAGE_MAX")
                        )
                )
                .outputVoltage(
                        new Thresholds(
                                requiredDouble("OUTPUT_VOLTAGE_MIN"),
                                requiredDouble("OUTPUT_VOLTAGE_MAX")
                        )
                )
                .frequency(
                        new Thresholds(
                                requiredDouble("FREQUENCY_MIN"),
                                requiredDouble("FREQUENCY_MAX")
                        )
                )
                .load(
                        new Thresholds(
                                0.0,
                                requiredDouble("LOAD_MAX_PERCENT")
                        )
                )
                .battery(
                        new Thresholds(
                                requiredDouble("BATTERY_MIN_PERCENT"),
                                100.0
                        )
                )
                .build();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("environment variable " + name + " is missing");
        }
        return value.trim();
    }

    private static URI requiredHttpUri(String name) {
        String value = required(name);

        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " is not a URL", e);
        }

        boolean speaksHttp = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        if (!speaksHttp || uri.getHost() == null) {
            throw new IllegalArgumentException(name + " must be an http or https URL with a host");
        }
        return uri;
    }

    private static int requiredInt(String name) {
        try {
            return Integer.parseInt(required(name));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " is not an integer", e);
        }
    }

    private static int requiredPositiveInt(String name) {
        int value = requiredInt(name);
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got " + value);
        }
        return value;
    }

    private static Duration requiredPositiveSeconds(String name) {
        return Duration.ofSeconds(requiredPositiveInt(name));
    }

    private static Duration requiredPositiveMilliseconds(String name) {
        return Duration.ofMillis(requiredPositiveInt(name));
    }

    private static int requiredPort(String name) {
        int value = requiredInt(name);
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException(name + " must be between 1 and 65535, got " + value);
        }
        return value;
    }

    /**
     * {@code Double.parseDouble} accepts "NaN" and "Infinity", and every comparison against NaN
     * is false — a threshold set to it would turn its parameter off with nothing to show for it.
     * Readings are already filtered for the same reason; this is the other half of that door.
     */
    private static double requiredDouble(String name) {
        double value;
        try {
            value = Double.parseDouble(required(name));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " is not a number", e);
        }

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number, got " + value);
        }
        return value;
    }

}
