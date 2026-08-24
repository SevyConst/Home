package org.example.ups.nut;

import org.example.ups.config.UpsClientConfig;
import org.example.ups.detect.StateMachine;
import org.example.ups.ha.HaPayload;
import org.example.ups.ha.HaPublisher;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The loop: read the UPS, work out what changed, and hand the result to Home Assistant.
 *
 * <p>A tick publishes for one of two reasons: the state machine saw something change, or the
 * heartbeat period has come round. Every message carries a full snapshot.
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
    private final LongSupplier nanoTime;

    private boolean anythingSent;
    private long lastSentNanos;

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
        this.nanoTime = nanoTime;
    }

    private UpsSnapshot pollOnce() {
        return nutClient.tryRead().orElseGet(UpsSnapshot::unreachable);
    }

    public void tick() {
        UpsSnapshot snapshot = pollOnce();

        long currentNanos = nanoTime.getAsLong();
        if (stateMachine.observe(snapshot) || heartbeatIsDue(currentNanos)) {
            publish(snapshot);
            anythingSent = true;

            lastSentNanos = currentNanos;
        }
    }

    private boolean heartbeatIsDue(long currentNanos) {
        if (!anythingSent) {
            return true;
        }

        Duration sinceLastSent = Duration.ofNanos(currentNanos - lastSentNanos);
        return sinceLastSent.compareTo(heartbeatPeriod) >= 0;
    }

    private void publish(UpsSnapshot snapshot) {
        try {
            haPublisher.submit(HaPayload.toJson(snapshot));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
