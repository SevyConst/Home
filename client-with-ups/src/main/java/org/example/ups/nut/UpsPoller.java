package org.example.ups.nut;

import org.example.ups.config.UpsClientConfig;
import org.example.ups.detect.StateMachine;
import org.example.ups.ha.HaPayload;
import org.example.ups.ha.HaPublisher;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * The loop: read the UPS, work out what changed, and hand the result to Home Assistant.
 *
 * <p>A tick publishes for one of three reasons: the state machine saw something change, the
 * heartbeat period has come round, or a burst is owing. Every message carries a full snapshot.
 *
 * <p>The burst is what the input voltage leaving its band gets instead of the single message
 * everything else gets: {@code INPUT_VOLTAGE_BURST_MESSAGES} of them,
 * {@code INPUT_VOLTAGE_BURST_PERIOD_SECONDS} apart. Only that direction, and only that parameter.
 *
 * <p>The cadence is measured on a monotonic source, not on the wall clock. Because the Pi has no
 * RTC. No wall clock reaches this class at all: a reading carries no time, and Home Assistant
 * stamps the message when it arrives.
 *
 */
public class UpsPoller {

    private final NutClient nutClient;
    private final StateMachine stateMachine;
    private final HaPublisher haPublisher;
    private final Duration heartbeatPeriod;
    private final int burstMessages;
    private final Duration burstPeriod;
    private final LongSupplier nanoTime;

    private boolean anythingSent;
    private long lastSentNanos;

    /**
     * How many messages of the current burst are still owed, the one going out now included.
     */
    private int burstRemaining;

    /**
     * {@code nanoTime} is the seam the tests use to move the monotonic source by hand rather than
     * sleeping. Nothing in production passes anything but {@code System::nanoTime}, which is the
     * only monotonic source the JDK offers — a wall clock here is the one mistake this class cannot
     * survive.
     */
    public UpsPoller(
            UpsClientConfig upsClientConfig,
            NutClient nutClient,
            HaPublisher haPublisher,
            LongSupplier nanoTime
    ) {

        this.nutClient = nutClient;
        this.stateMachine = new StateMachine(
                upsClientConfig.inputVoltage(),
                upsClientConfig.outputVoltage(),
                upsClientConfig.load(),
                upsClientConfig.battery()
        );
        this.haPublisher = haPublisher;
        this.heartbeatPeriod = upsClientConfig.haHeartbeatPeriod();
        this.burstMessages = upsClientConfig.inputVoltageBurstMessages();
        this.burstPeriod = upsClientConfig.inputVoltageBurstPeriod();
        this.nanoTime = nanoTime;
    }

    public void tick() {
        UpsSnapshot snapshot = nutClient.tryRead();

        long currentNanos = nanoTime.getAsLong();

        boolean changed = stateMachine.observe(snapshot);
        if (stateMachine.isInputVoltageNewlyOutOfRange()) {
            burstRemaining = burstMessages;
        }

        if (changed || burstIsDue(currentNanos) || heartbeatIsDue(currentNanos)) {
            publish(snapshot);
            anythingSent = true;

            lastSentNanos = currentNanos;
            if (burstRemaining > 0) {
                burstRemaining--;
            }
        }
    }

    /**
     * The heartbeat needs no separate holding off during a burst: a burst message is a message like
     * any other, and moves {@link #lastSentNanos} with it.
     */
    private boolean heartbeatIsDue(long currentNanos) {
        if (!anythingSent) {
            return true;
        }

        return sinceLastSent(currentNanos).compareTo(heartbeatPeriod) >= 0;
    }

    private boolean burstIsDue(long currentNanos) {
        return burstRemaining > 0 && sinceLastSent(currentNanos).compareTo(burstPeriod) >= 0;
    }

    private Duration sinceLastSent(long currentNanos) {
        return Duration.ofNanos(currentNanos - lastSentNanos);
    }

    private void publish(UpsSnapshot snapshot) {
        try {
            haPublisher.submit(HaPayload.toJson(snapshot));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
