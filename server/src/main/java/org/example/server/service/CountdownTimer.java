package org.example.server.service;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CountdownTimer {

    public static final double COEFFICIENT_WAITING = 2;

    public static final String OFFLINE = " оффлайн";

    private final ScheduledExecutorService scheduler;
    private final String deviceId;
    private final TelegramNotifier telegramNotifier;
    private ScheduledFuture<?> currentTask;

    @Getter
    @Setter
    private volatile boolean isOffline = false;

    @Getter
    @Setter
    private volatile LocalDateTime lastOnlineTime;

    @Getter
    private final Lock lock = new ReentrantLock();

    public CountdownTimer(String deviceId, TelegramNotifier telegramNotifier) {
        this.deviceId = deviceId;
        this.telegramNotifier = telegramNotifier;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "timer-" + deviceId);
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }

        currentTask = scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                isOffline = true;
                telegramNotifier.send(deviceId + OFFLINE);
            } finally {
                lock.unlock();
            }
            currentTask.cancel(true);
        }, (long) (EventService.getPeriodMilliseconds() * COEFFICIENT_WAITING), Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }


    public void shutdown() {
        scheduler.shutdownNow();
    }
}
