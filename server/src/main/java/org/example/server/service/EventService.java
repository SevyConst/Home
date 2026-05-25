package org.example.server.service;

import lombok.RequiredArgsConstructor;
import model.Event;
import model.EventRequest;
import model.EventType;
import org.example.server.model.Messages;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final DateTimeFormatter formatterWithoutSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TelegramNotifier telegramNotifier;

    public static volatile Long SleepSeconds = 30L;

    ConcurrentHashMap<String, CountdownTimer> countdownTimersMap = new ConcurrentHashMap<>();

    public void processEvents(EventRequest request) {

        // TODO: check deviceId (search in the db)
        // TODO: raspberry pi (as http-client) doesn't have its own time - process this case

        String deviceId = request.getDeviceId();

        CountdownTimer countdownTimer = countdownTimersMap.get(deviceId);
        boolean firstLaunch = false;
        if (null == countdownTimer) {
            firstLaunch = true;
            countdownTimer = new CountdownTimer(deviceId, telegramNotifier);
            countdownTimersMap.put(deviceId, countdownTimer);
        }

        countdownTimer.getLock().lock();
        try {
            Messages messages = generateMessages(
                    countdownTimer,
                    request.getEvents(),
                    deviceId,
                    firstLaunch
            );

            // TODO: process message for user
            if (!messages.messageForAdmin().isEmpty()) {
                telegramNotifier.send(messages.messageForAdmin());
            }
        } finally {
            countdownTimer.getLock().unlock();
        }
    }

    private Messages generateMessages(
            CountdownTimer countdownTimer,
            List<Event> events,
            String deviceId,
            boolean firstLaunch) {
        countdownTimer.setLastOnlineTime(LocalDateTime.now());
        countdownTimer.setOffline(false);
        countdownTimer.start();

        StringBuilder messageForUser = new StringBuilder();
        StringBuilder messageForAdmin = new StringBuilder();

        boolean hasError = false;

        StringBuilder messagePart = new StringBuilder();
        for (int i = 0; i < events.size() && !hasError; i++) {
            Event currentEvent = events.get(i);
            switch (currentEvent.getEventType()) {
                case EventType.START:
                    messagePart.append(createMessageForStart(i, countdownTimer, events, firstLaunch));
                    messageForUser.append(messagePart);
                    messageForAdmin.append(messagePart);
                    break;
                case EventType.ERROR:
                    hasError = true;
                    messagePart = new StringBuilder();
                    messagePart.append("\nошибка в ")
                            .append(removeSeconds(currentEvent.getTime()));
                    messageForUser.append(messagePart);
                    messageForAdmin.append(messagePart)
                            .append(" : \"")
                            .append(currentEvent.getAdditionalInfo())
                            .append("\"");
                    break;
                case EventType.PING:
                    if (0 == i) {
                        if (firstLaunch) {
                            messageForAdmin.append("было добавлено в ")
                                    .append(removeSeconds(currentEvent.getTime()));

                        } else if (countdownTimer.isOffline()) {
                            messagePart.append(createOfflineMessageForPing(
                                    countdownTimer,
                                    currentEvent,
                                    events.size() == 1
                            ));
                            messageForUser.append(messagePart);
                            messageForAdmin.append(messagePart);
                        }
                        break;
                    }
            }
        }

        if (!messageForAdmin.isEmpty()) {
            messageForAdmin = new StringBuilder(deviceId)
                    .append(": ")
                    .append(messageForAdmin);
        }

        if (!messageForUser.isEmpty()) {
            messageForUser = new StringBuilder(deviceId)
                    .append(": ")
                    .append(messageForUser);
        }

        return new Messages(messageForAdmin.toString(), messageForUser.toString());
    }

    private String createMessageForStart (
            int i,
            CountdownTimer countdownTimer,
            List<Event> events,
            boolean firstLaunch
    ) {
        Event currentEvent = events.get(i);

        if (0 == i && firstLaunch) {
            return "\nвключился в " + removeSeconds(currentEvent.getTime());
        }

        StringBuilder result = new StringBuilder();
        result.append("\nпитание было отключено ");
        String beginning = 0 == i
                ? countdownTimer.getLastOnlineTime().format(formatterWithoutSeconds)
                : removeSeconds(events.get(i - 1).getTime());
        String end = removeSeconds(currentEvent.getTime());
        if (beginning.equals(end)) {
            result.append("на короткий период (не дольше минуты) в ")
                    .append(beginning);
        } else {
            result.append("с ")
                    .append(beginning)
                    .append(" до ")
                    .append(end);
        }

        return result.toString();
    }

    private String createOfflineMessageForPing(
            CountdownTimer countdownTimer,
            Event currentEvent,
            boolean isLastEvent) {
        String beginning = countdownTimer.getLastOnlineTime().format(formatterWithoutSeconds);
        String end = isLastEvent
                ? LocalDateTime.now().format(formatterWithoutSeconds)
                : removeSeconds(currentEvent.getTime());

        StringBuilder result = new StringBuilder().append("\nне было интернета ");
        if (beginning.equals(end)) {
            result.append("в короткий промежуток времени (меньше минуты) в ")
                    .append(beginning);
        } else {
            result.append("с " )
                    .append(beginning)
                    .append(" до ")
                    .append(end);
        }

        return result.toString();
    }

    private String removeSeconds(String time) {
        return time.substring(0, time.lastIndexOf(':'));
    }

}
