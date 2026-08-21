package org.example.ups.detect;

import org.example.ups.config.Thresholds;
import org.example.ups.nut.UpsSnapshot;
import org.example.ups.nut.UpsStatus;

import java.util.OptionalDouble;

public class StateMachine {

    private final Band inputVoltage;
    private final Band outputVoltage;
    private final Band frequency;
    private final Band load;
    private final Band battery;

    /** The status the last message carried, which is what a new one has to differ from. */
    private UpsStatus shown = UpsStatus.UNREACHABLE;

    /**
     * The line state of the last <em>readable</em> reading, which is a different thing from
     * {@link #shown}: it survives a gap in the readings, so that a UPS coming back on the mains
     * it was already on is not read as a switch.
     */
    private Boolean mainsPresent;

    public StateMachine(
            Thresholds inputVoltage,
            Thresholds outputVoltage,
            Thresholds frequency,
            Thresholds load,
            Thresholds battery
    ) {

        this.inputVoltage = new Band(inputVoltage);
        this.outputVoltage = new Band(outputVoltage);
        this.frequency = new Band(frequency);
        this.load = new Band(load);
        this.battery = new Band(battery);
    }

    /**
     * @return whether anything changed, which is all the caller branches on
     */
    public boolean observe(UpsSnapshot snapshot) {
        UpsStatus status = snapshot.getStatus();
        boolean changed = status != shown;
        shown = status;

        if (status == UpsStatus.UNREACHABLE || status == UpsStatus.UNKNOWN) {
            return changed;
        }

        boolean mainsPresentNow = status == UpsStatus.ONLINE;
        boolean mainsSwitched = mainsPresent != null && mainsPresentNow != mainsPresent;
        mainsPresent = mainsPresentNow;

        if (mainsPresentNow && !mainsSwitched) {
            changed |= inputVoltage.observe(snapshot.getInputVoltage());
            changed |= frequency.observe(snapshot.getInputFrequency());
        }

        changed |= outputVoltage.observe(snapshot.getOutputVoltage());
        changed |= load.observe(snapshot.getLoadPercent());
        changed |= battery.observe(snapshot.getBatteryCharge());

        return changed;
    }

    private static class Band {

        private final Thresholds thresholds;

        /** Whether the last message about this parameter said the UPS had stopped sending it */
        private boolean absent;

        /** Which side of the band the last message about this parameter reported */
        private boolean outOfRange;

        private Band(Thresholds thresholds) {
            this.thresholds = thresholds;
        }

        /** @return whether this reading is worth a message about this parameter */
        private boolean observe(OptionalDouble reading) {
            boolean absentNow = reading.isEmpty();
            boolean changed = absentNow != absent;
            absent = absentNow;

            if (absentNow) {
                return changed;
            }

            boolean outOfRangeNow = thresholds.isOutOfRange(reading.getAsDouble());
            if (outOfRangeNow != outOfRange) {
                outOfRange = outOfRangeNow;
                changed = true;
            }

            return changed;
        }
    }
}
