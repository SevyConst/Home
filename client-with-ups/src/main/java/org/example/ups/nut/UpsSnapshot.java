package org.example.ups.nut;

import lombok.Getter;
import lombok.Setter;

import java.util.Optional;
import java.util.OptionalDouble;

@Getter
@Setter
public class UpsSnapshot {

    private UpsStatus status = UpsStatus.UNKNOWN;

    private OptionalDouble inputVoltage = OptionalDouble.empty();
    private OptionalDouble outputVoltage = OptionalDouble.empty();
    private OptionalDouble inputFrequency = OptionalDouble.empty();
    private OptionalDouble loadPercent = OptionalDouble.empty();
    private OptionalDouble batteryCharge = OptionalDouble.empty();
    private OptionalDouble batteryRuntimeMinutes = OptionalDouble.empty();
    private OptionalDouble batteryVoltage = OptionalDouble.empty();
    private Optional<String> beeper = Optional.empty();
    private Optional<String> testResult = Optional.empty();

    public static UpsSnapshot unreachable() {
        UpsSnapshot snapshot = new UpsSnapshot();
        snapshot.setStatus(UpsStatus.UNREACHABLE);
        return snapshot;
    }
}
