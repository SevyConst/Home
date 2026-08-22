package org.example.ups;

import lombok.extern.slf4j.Slf4j;
import org.example.ConstantsKt;
import org.example.ups.config.UpsClientConfig;
import org.example.ups.ha.HaPublisher;
import org.example.ups.nut.NutClient;
import org.example.ups.nut.UpsPoller;
import org.example.ups.server.EventSender;
import org.example.ups.server.ServerReporter;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Starts its working threads: one reading the UPS, one sending to Home Assistant, and — unless
 * SERVER_ENABLED is false — one sending the heartbeat to the server.
 */
@Slf4j
public class Main {

    void main() {
        UpsClientConfig upsClientConfig = UpsClientConfig.readEnv();

        log.info("Configuration: {}", upsClientConfig);

        HaPublisher publisher = new HaPublisher(upsClientConfig);

        NutClient nutClient = new NutClient(upsClientConfig);

        ScheduledExecutorService pollerExecutor =
                Executors.newSingleThreadScheduledExecutor(threadFactory("ups-poller"));

        ExecutorService haExecutor = Executors.newSingleThreadExecutor(threadFactory("ha-publisher"));

        UpsPoller poller = new UpsPoller(
                upsClientConfig,
                nutClient,
                publisher,
                System::nanoTime
        );

        haExecutor.execute(exitOnFailure("Sending to HA", publisher));

        if (upsClientConfig.serverEnabled()) {
            startServerReporting(upsClientConfig);
        } else {
            log.info("SERVER_ENABLED is false: nothing is sent to the server");
        }

        pollerExecutor.scheduleWithFixedDelay(
                exitOnFailure("Polling the UPS", poller::tick),
                0,
                upsClientConfig.upsPollPeriod().toMillis(),
                TimeUnit.MILLISECONDS
        );

        log.info(
                "Started: polling {} every {}",
                upsClientConfig.nutUpsName(),
                upsClientConfig.upsPollPeriod()
        );
    }

    private static void startServerReporting(UpsClientConfig upsClientConfig) {
        EventSender eventSender = new EventSender(upsClientConfig);

        Clock clock = Clock.systemDefaultZone();
        ServerReporter reporter = new ServerReporter(eventSender, upsClientConfig.deviceId(), clock);

        ScheduledExecutorService pingExecutor =
                Executors.newSingleThreadScheduledExecutor(threadFactory("server-ping"));

        pingExecutor.execute(exitOnFailure("Sending START", reporter::sendStart));

        pingExecutor.scheduleWithFixedDelay(
                exitOnFailure("Sending PING", reporter::sendPing),
                ConstantsKt.PING_PERIOD_MILLISECONDS,
                ConstantsKt.PING_PERIOD_MILLISECONDS,
                TimeUnit.MILLISECONDS
        );

        log.info("Reporting to {}", upsClientConfig.serverUri());
    }

    private static Runnable exitOnFailure(String description, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                log.error(description, t);
                System.exit(1);
            }
        };
    }

    static ThreadFactory threadFactory(String name) {
        return runnable -> new Thread(runnable, name);
    }
}
