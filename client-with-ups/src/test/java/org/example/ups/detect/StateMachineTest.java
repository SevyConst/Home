package org.example.ups.detect;

import org.example.ups.config.Thresholds;
import org.example.ups.nut.UpsSnapshot;
import org.example.ups.nut.UpsStatus;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMachineTest {

    private StateMachine machine() {
        return new StateMachine(
                new Thresholds(200.0, 245.0),
                new Thresholds(200.0, 245.0),
                new Thresholds(49.0, 51.0),
                new Thresholds(0.0, 80.0),
                new Thresholds(30.0, 100.0)
        );
    }

    private UpsSnapshot online(double volts) {
        return snapshot(
                UpsStatus.ONLINE,
                volts,
                230.0,
                50.0,
                20.0,
                100.0
        );
    }

    /** On the mains, with everything else nominal and the output at the given voltage. */
    private UpsSnapshot withOutput(double volts) {
        return snapshot(
                UpsStatus.ONLINE,
                230.0,
                volts,
                50.0,
                20.0,
                100.0
        );
    }

    private UpsSnapshot onBattery() {
        return onBatteryWithOutput(230.0);
    }

    /** Running on the battery, with the inverter putting out the given voltage. */
    private UpsSnapshot onBatteryWithOutput(double volts) {
        return snapshot(
                UpsStatus.ON_BATTERY,
                0.0,
                volts,
                0.0,
                20.0,
                100.0
        );
    }

    /** Back on the mains, with the input readings of the outage the driver has yet to refresh. */
    private UpsSnapshot restoredWithStaleInput() {
        return restoredWithStaleInputAndOutput(230.0);
    }

    /** The same moment, with the output — which the driver does not lag on — given explicitly. */
    private UpsSnapshot restoredWithStaleInputAndOutput(double volts) {
        return snapshot(
                UpsStatus.ONLINE,
                0.0,
                volts,
                0.0,
                20.0,
                100.0
        );
    }

    /** Answering, and reporting a line state this client does not model, such as {@code BYPASS}. */
    private UpsSnapshot unreadableStatus() {
        return snapshot(
                UpsStatus.UNKNOWN,
                230.0,
                230.0,
                50.0,
                20.0,
                100.0
        );
    }

    private UpsSnapshot withLoad(double load) {
        return snapshot(
                UpsStatus.ONLINE,
                230.0,
                230.0,
                50.0,
                load,
                100.0
        );
    }

    private UpsSnapshot withBattery(double charge) {
        return snapshot(
                UpsStatus.ONLINE,
                230.0,
                230.0,
                50.0,
                20.0,
                charge
        );
    }

    /** Readable, on the mains, and no longer reporting its charge. */
    private UpsSnapshot withoutBattery() {
        UpsSnapshot snapshot = new UpsSnapshot();
        snapshot.setStatus(UpsStatus.ONLINE);
        snapshot.setInputVoltage(OptionalDouble.of(230.0));
        snapshot.setOutputVoltage(OptionalDouble.of(230.0));
        snapshot.setInputFrequency(OptionalDouble.of(50.0));
        snapshot.setLoadPercent(OptionalDouble.of(20.0));
        return snapshot;
    }

    /** Every watched parameter outside its range at once. */
    private UpsSnapshot allOutOfRange() {
        return snapshot(
                UpsStatus.ONLINE,
                190.0,
                190.0,
                47.0,
                90.0,
                20.0
        );
    }

    /** A reply that carried no {@code ups.status} line at all, and no output reading either. */
    private UpsSnapshot withoutStatus() {
        UpsSnapshot snapshot = new UpsSnapshot();
        snapshot.setInputVoltage(OptionalDouble.of(230.0));
        snapshot.setLoadPercent(OptionalDouble.of(20.0));
        snapshot.setBatteryCharge(OptionalDouble.of(100.0));
        return snapshot;
    }

    private UpsSnapshot snapshot(
            UpsStatus status,
            double inputVolts,
            double outputVolts,
            double frequency,
            double load,
            double charge
    ) {
        UpsSnapshot snapshot = new UpsSnapshot();
        snapshot.setStatus(status);
        snapshot.setInputVoltage(OptionalDouble.of(inputVolts));
        snapshot.setOutputVoltage(OptionalDouble.of(outputVolts));
        snapshot.setInputFrequency(OptionalDouble.of(frequency));
        snapshot.setLoadPercent(OptionalDouble.of(load));
        snapshot.setBatteryCharge(OptionalDouble.of(charge));
        return snapshot;
    }

    @Test
    void firstReadingIsWorthAMessageAndOnlyEstablishesTheBaseline() {
        StateMachine machine = machine();

        assertTrue(machine.observe(online(190.0)));
        assertFalse(machine.observe(online(190.0)));
    }

    @Test
    void losingAndRegainingMainsOneSecondApartIsReportedBothTimes() {
        StateMachine machine = machine();
        machine.observe(online(230.0));

        assertTrue(machine.observe(onBattery()));
        assertTrue(machine.observe(online(230.0)));
    }

    @Test
    void mainsComingBackToAStillEmptyBatteryIsReportedAtOnce() {
        StateMachine machine = machine();
        machine.observe(onBattery());

        assertTrue(machine.observe(withBattery(12.0)));
        assertFalse(machine.observe(withBattery(12.0)));
    }

    @Test
    void readingWithNoStatusAtAllIsShownButJudgedNeitherWay() {
        StateMachine machine = machine();
        machine.observe(online(230.0));

        UpsSnapshot noStatus = withoutStatus();

        assertTrue(machine.observe(noStatus));
        assertTrue(machine.observe(online(190.0)));
        assertFalse(machine.observe(online(190.0)));
    }

    @Test
    void inputIsNotJudgedOnTheSnapshotThatReportsTheMainsBack() {
        StateMachine machine = machine();
        machine.observe(online(230.0));
        machine.observe(onBattery());

        assertTrue(machine.observe(restoredWithStaleInput()));
        assertFalse(machine.observe(online(230.0)));
    }

    /**
     * A sag that the outage interrupted is still open: the band has the input recorded as out of
     * range, and a sagging voltage is the last thing said about it. Whatever the recovery tick
     * does with the input, the first reading that can be trusted has to close it.
     */
    @Test
    void inputVoltageThatSaggedBeforeTheOutageIsReportedNormalOnceTheInputIsReadableAgain() {
        StateMachine machine = machine();
        machine.observe(online(230.0));
        machine.observe(online(190.0));
        machine.observe(onBattery());
        machine.observe(restoredWithStaleInput());

        assertTrue(machine.observe(online(230.0)));
    }

    @Test
    void batteryIsStillWatchedWhileRunningOnIt() {
        StateMachine machine = machine();
        machine.observe(online(230.0));
        machine.observe(onBattery());

        assertTrue(
                machine.observe(
                        snapshot(
                                UpsStatus.ON_BATTERY,
                                0.0,
                                230.0,
                                0.0,
                                20.0,
                                20.0
                        )
                )
        );
    }

    /** Out, still out, back in, still in — one message for each side it changes to and no more. */
    @Test
    void inputVoltageLeavingItsRangeAndReturningIsReportedOnceEachWay() {
        StateMachine machine = machine();
        machine.observe(online(230.0));

        assertTrue(machine.observe(online(199.0)));
        assertFalse(machine.observe(online(190.0)));

        // Just inside the range is enough: there is no margin to clear.
        assertTrue(machine.observe(online(201.0)));

        // And staying inside reports nothing further.
        assertFalse(machine.observe(online(210.0)));
    }

    @Test
    void outputVoltageLeavingItsRangeAndReturningIsReportedOnceEachWay() {
        StateMachine machine = machine();
        machine.observe(online(230.0));

        assertTrue(machine.observe(withOutput(190.0)));
        assertFalse(machine.observe(withOutput(189.0)));
        assertTrue(machine.observe(withOutput(230.0)));
    }

    /**
     * The output is where this band earns its keep, and the reason it is not silenced during an
     * outage the way the input is. The input reads zero because there is nothing on the line; the
     * output is the UPS's own inverter, and an outage is exactly when a failing one shows itself.
     *
     * <p>Nothing else on this reading could account for the message: the mains did not switch, and
     * with them down the input and frequency are not judged at all.
     */
    @Test
    void outputIsJudgedWhileTheMainsAreDown() {
        StateMachine machine = machine();
        machine.observe(online(230.0));
        machine.observe(onBattery());

        assertTrue(machine.observe(onBatteryWithOutput(190.0)));
    }

    /**
     * And not silenced on the tick that reports the mains back either. That tick withholds
     * judgement of the input because {@code usbhid-ups} refreshes it late, but nothing lags the
     * output, so a sagging one is recorded here rather than a tick later.
     *
     * <p>The restore makes the first assertion true whatever the output does, so the second
     * reading is the one that shows it: the return to normal is news only because the sag was
     * recorded above, and a band that had never seen the sag would find nothing here to report.
     */
    @Test
    void outputIsJudgedOnTheSnapshotThatReportsTheMainsBack() {
        StateMachine machine = machine();
        machine.observe(online(230.0));
        machine.observe(onBattery());

        assertTrue(machine.observe(restoredWithStaleInputAndOutput(190.0)));
        assertTrue(machine.observe(withOutput(230.0)));
    }

    @Test
    void loadAboveTheCapIsReportedAndSoIsItsReturn() {
        StateMachine machine = machine();
        machine.observe(online(230.0));

        assertTrue(machine.observe(withLoad(90.0)));
        assertTrue(machine.observe(withLoad(60.0)));
        assertFalse(machine.observe(withLoad(61.0)));
    }

    @Test
    void frequencyIsWatchedTheSameWayAsInputVoltage() {
        StateMachine machine = machine();
        machine.observe(online(230.0));

        assertTrue(
                machine.observe(
                        snapshot(
                                UpsStatus.ONLINE,
                                230.0,
                                230.0,
                                47.0,
                                20.0,
                                100.0
                        )
                )
        );
        assertTrue(machine.observe(online(230.0)));
    }

    /**
     * All four ranges are folded in on every reading, whatever the ones before them reported. The
     * repeat is what shows it: a {@code ||} chain would stop at the first parameter to change and
     * leave the other three unrecorded, so the identical reading a second later would look like
     * news about whichever of them the chain never reached.
     */
    @Test
    void severalParametersLeavingTheirRangesAtOnceAreAllRecorded() {
        StateMachine machine = machine();
        machine.observe(online(230.0));

        assertTrue(machine.observe(allOutOfRange()));
        assertFalse(machine.observe(allOutOfRange()));
    }

    /**
     * A reading that moved without leaving its range is not news. Every message carries the whole
     * snapshot, so the new value travels with the next one anyway — and battery runtime and
     * battery voltage are not watched here at all, for the same reason.
     */
    @Test
    void readingsThatMoveInsideTheirRangesAreNotWorthAMessage() {
        StateMachine machine = machine();
        UpsSnapshot first = snapshot(
                UpsStatus.ONLINE,
                230.0,
                230.0,
                50.0,
                20.0,
                100.0
        );
        first.setBatteryRuntimeMinutes(OptionalDouble.of(50.0));
        first.setBatteryVoltage(OptionalDouble.of(13.6));
        machine.observe(first);

        UpsSnapshot second = snapshot(
                UpsStatus.ONLINE,
                228.0,
                230.0,
                49.9,
                23.0,
                97.0
        );
        second.setBatteryRuntimeMinutes(OptionalDouble.of(25.0));
        second.setBatteryVoltage(OptionalDouble.of(13.2));

        assertFalse(machine.observe(second));
    }

    /**
     * A UPS that keeps answering but stops sending one of its readings leaves Home Assistant
     * holding a number nothing stands behind any more, so the entity has to be shown as having no
     * value. Only once, though: a message per tick for as long as the reading is absent would say
     * nothing new.
     */
    @Test
    void readingThatStopsArrivingIsReportedOnce() {
        StateMachine machine = machine();
        machine.observe(online(230.0));

        assertTrue(machine.observe(withoutBattery()));
        assertFalse(machine.observe(withoutBattery()));
    }

    /**
     * And the return is the same event in reverse — the entity has a value again — whatever the
     * value happens to be. This one is still below its threshold, so nothing about the range
     * changed; the message is for the reading being back at all.
     */
    @Test
    void readingThatComesBackIsReportedWhateverItSays() {
        StateMachine machine = machine();
        machine.observe(online(230.0));
        machine.observe(withBattery(25.0));
        machine.observe(withoutBattery());

        assertTrue(machine.observe(withBattery(25.0)));
    }

    /**
     * What the gap must not do is leave the range confused about which side of it we are on. The
     * reading that ends the gap sets that side, so the tick after it is silent while the value
     * stays bad, and the recovery is still reported when it comes.
     *
     * <p>The charge has to cross the threshold across the gap — full before it, low after — or the
     * side would already be right from the readings before the gap and the reading that ends it
     * would have nothing to set.
     */
    @Test
    void rangeIsJudgedAgainFromTheReadingThatEndsTheGap() {
        StateMachine machine = machine();
        machine.observe(online(230.0));
        machine.observe(withoutBattery());
        machine.observe(withBattery(25.0));

        assertFalse(machine.observe(withBattery(24.0)));
        assertTrue(machine.observe(withBattery(100.0)));
    }

    /**
     * A UPS that has gone quiet is worth a message — the entities have to go unavailable — but
     * only the one: a message per tick for as long as it stays quiet would say nothing new.
     */
    @Test
    void unreachableUpsIsReportedOnceAndSoIsItsReturn() {
        StateMachine machine = machine();
        machine.observe(online(230.0));

        assertTrue(machine.observe(UpsSnapshot.unreachable()));
        assertFalse(machine.observe(UpsSnapshot.unreachable()));
        assertTrue(machine.observe(online(230.0)));
    }

    /**
     * Home Assistant is told once and then left alone. The entities are already unavailable; a
     * message per tick for as long as the driver stays confused would say nothing new.
     */
    @Test
    void unreadableStatusIsShownOnceAndThenLeftAlone() {
        StateMachine machine = machine();
        machine.observe(online(230.0));

        assertTrue(machine.observe(unreadableStatus()));
        assertFalse(machine.observe(unreadableStatus()));
    }

    /**
     * Reachable and unreadable are two different states, not one. A UPS that starts answering
     * again has to be shown even when its line state is still unplaceable, because the raw status
     * and every number in the payload come back with it.
     */
    @Test
    void upsThatComesBackWithoutAReadableStatusIsStillShownAsReachable() {
        StateMachine machine = machine();
        machine.observe(online(230.0));
        machine.observe(UpsSnapshot.unreachable());

        assertTrue(machine.observe(unreadableStatus()));
    }

    /**
     * The outage began out of sight and is recognised on the first reading that comes back, which
     * is what makes its end an event too: a machine that had not recorded the outage would have
     * nothing to report when the mains came back.
     *
     * <p>The first assertion is true from the UPS answering again whatever its line state says, so
     * the last two are the ones that show the outage was recorded: the restore is news, and the
     * tick after it is not.
     */
    @Test
    void outageIsStillSeenWhenItStraddlesAGapInTheReadings() {
        StateMachine machine = machine();
        machine.observe(online(230.0));
        machine.observe(UpsSnapshot.unreachable());

        assertTrue(machine.observe(onBattery()));
        assertTrue(machine.observe(online(230.0)));
        assertFalse(machine.observe(online(230.0)));
    }
}
