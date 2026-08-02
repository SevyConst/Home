package org.example.server.service;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.example.server.model.dto.Chat;
import org.example.server.repository.DeviceRepository;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class DeviceProcessor {

    public static final double COEFFICIENT_WAITING = 2;

    public static final String OFFLINE =
                    """
                    %s недоступен. Возможные причины:
                    1. Либо произошел сбой интернета.
                    2. Либо отключилось питание у %s.
                    
                    Произошло 1 или 2 - станет понятно после восстановления связи с %s
                    """;

    private final ScheduledExecutorService scheduler;
    private final String deviceId;
    private final TelegramNotifier telegramNotifier;
    private final DeviceRepository deviceRepository;

    // guarded by lock
    private ScheduledFuture<?> currentTask;

    // guarded by lock
    private long generation = 0;

    // guarded by lock
    @Getter
    @Setter
    private boolean firstRequestProcessed = false;

    // guarded by lock; once true, this processor is no longer in the map and must not be used
    @Getter
    @Setter
    private boolean removed = false;

    @Getter
    @Setter
    private volatile boolean isOffline = false;

    @Getter
    @Setter
    private volatile ZonedDateTime lastOnlineTime;

    @Getter
    private final Map<Long, Integer> chatIdToRepliedMessageIdMap = new HashMap<>();

    @Getter
    private final Lock lock = new ReentrantLock();

    public DeviceProcessor(
            String deviceId,
            TelegramNotifier telegramNotifier,
            DeviceRepository deviceRepository
    ) {
        this.deviceId = deviceId;
        this.telegramNotifier = telegramNotifier;
        this.deviceRepository = deviceRepository;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "timer-" + deviceId);
            thread.setDaemon(true);
            return thread;
        });
    }

    // must be called with lock held
    public void startCountdownTimer() {
        if (currentTask != null) {
            currentTask.cancel(false);
        }

        long myGeneration = ++generation;
        currentTask = scheduler.schedule(
                () -> {
                    lock.lock();
                    try {
                        if (myGeneration != generation) {
                            return;
                        }
                        sendMessagesAboutPause();
                    } finally {
                        lock.unlock();
                    }
                },
                (long) (EventService.getPeriodMilliseconds() * COEFFICIENT_WAITING),
                TimeUnit.MILLISECONDS
        );
    }

    private void sendMessagesAboutPause() {
        isOffline = true;
        List<Chat> chatsList = deviceRepository.getDeviceChats(deviceId);
        String text = String.format(OFFLINE, deviceId, deviceId, deviceId);

        for (Chat chat : chatsList) {
            Long chatId = chat.chatId();
            try {
                Integer messageId = telegramNotifier.send(
                        text,
                        chatId,
                        Optional.empty()
                );
                chatIdToRepliedMessageIdMap.put(chatId, messageId);
            } catch (TelegramApiException e) {
                log.error("Failed to send text to Telegram: '{}' to chat ID: {}", text, chatId, e);
            }
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
