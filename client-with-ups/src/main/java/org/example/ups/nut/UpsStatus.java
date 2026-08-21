package org.example.ups.nut;

import java.util.Arrays;
import java.util.Set;

/**
 * What is known about the UPS from one reading, which is either a line state it reported or the
 * reason there is no line state to report.
 */
public enum UpsStatus {

    ONLINE,
    ON_BATTERY,

    /** The UPS answered, but its {@code ups.status} held no flag this client can place. */
    UNKNOWN,

    /**
     * The UPS did not answer at all, so nothing about it was read. Never comes from
     * {@link #from(String)} — only a failed read produces it, and such a reading carries no
     * values beside it.
     */
    UNREACHABLE;

    static UpsStatus from(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return UNKNOWN;
        }

        // Set.copyOf, not Set.of: Set.of throws on a duplicate
        Set<String> flags = Set.copyOf(Arrays.asList(rawStatus.trim().split("\\s+")));

        if (flags.contains("OB")) {
            return ON_BATTERY;
        }
        if (flags.contains("OL")) {
            return ONLINE;
        }
        return UNKNOWN;
    }
}
