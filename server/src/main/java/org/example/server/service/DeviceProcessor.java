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
    private ScheduledFuture<?> currentTask;

    @Getter
    @Setter
    private volatile boolean isOffline = false;

    @Getter
    @Setter
    private volatile ZonedDateTime lastOnlineTime;

    @Getter
    private final Map<Long, Integer> chatIdToRepliedMessageIdMap = new HashMap<>();

    @Getter
    @Setter
    private volatile Long lastEventId;

    @Getter
    @Setter
    private boolean hasError = false;

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

    public void startCountdownTimer() {
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }

        currentTask = scheduler.scheduleAtFixedRate(
                () -> {
                    lock.lock();
                    try {
                        sendMessagesAboutPause();
                    } finally {
                        lock.unlock();
                    }
                    currentTask.cancel(true);
                },
                (long) (EventService.getPeriodMilliseconds() * COEFFICIENT_WAITING),
                Long.MAX_VALUE,
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
