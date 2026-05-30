package org.example.server.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import model.Event;
import model.EventRequest;
import model.EventType;
import org.example.FormattersKt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class EventService {

    @Getter
    @Setter
    private static volatile Long periodMilliseconds = 30L * 1000;

    public static final DateTimeFormatter formatterWithoutSeconds = DateTimeFormatter.ofPattern("HH:mm yyyy-MM-dd");

    public static final String ADDED = "\nбыл добавлен";
    public static final String TURN_ON = "\nвключился в ";
    public static final String NO_POWER = "\nпитание было отключено с ";
    public static final String UNTIL = " до ";
    public static final String WITHOUT_INTERNET = "\nне было интернета с ";
    public static final String ERROR = "\nошибка в ";

    private final TelegramNotifier telegramNotifier;

    ConcurrentHashMap<String, DeviceProcessor> countdownTimersMap = new ConcurrentHashMap<>();

    public void processEvents(EventRequest request) {

        // TODO: check deviceId (search in the db)
        // TODO: raspberry pi (as http-client) doesn't have its own time - process this case

        String deviceId = request.getDeviceId();

        DeviceProcessor deviceProcessor = countdownTimersMap.get(deviceId);
        boolean firstLaunch = false;
        if (null == deviceProcessor) {
            firstLaunch = true;
            deviceProcessor = new DeviceProcessor(deviceId, telegramNotifier);
            countdownTimersMap.put(deviceId, deviceProcessor);
        }

        if (deviceProcessor.isHasError()) {
            return;
        }

        deviceProcessor.getLock().lock();
        try {
            String messageFirstLine = deviceId + ":";
            StringBuilder messageForUser = new StringBuilder(messageFirstLine);
            StringBuilder messageForAdmin = new StringBuilder(messageFirstLine);

            processEvents(
                    deviceProcessor,
                    request.getEvents(),
                    firstLaunch,
                    messageForUser,
                    messageForAdmin
            );

            deviceProcessor.setLastOnlineTime(LocalDateTime.now());
            deviceProcessor.setOffline(false);
            if (!deviceProcessor.isHasError()) {
                deviceProcessor.startCountdownTimer();
            }

            // TODO: process message for user
            String messageForAdminString = messageForAdmin.toString();
            if (!messageFirstLine.equals(messageForAdminString)) {
                telegramNotifier.send(messageForAdminString);
            }
        } finally {
            deviceProcessor.getLock().unlock();
        }
    }

    private void processEvents(
            DeviceProcessor deviceProcessor,
            List<Event> events,
            boolean firstLaunch,
            StringBuilder messageForUser,
            StringBuilder messageForAdmin
    ) {
        Optional<Integer> firstErrorIndex = getFirstIndexError(events);
        if  (firstErrorIndex.isPresent()) {
            deviceProcessor.setHasError(true);
        }
        EventType firstEventType = events.getFirst().getEventType();
        if (firstLaunch) {
            messageForAdmin.append(ADDED);
        }
        if (deviceProcessor.isOffline() && !(events.size() == 1 && firstEventType == EventType.START)) {
            String timeFirstErrorOrLastEvent = firstErrorIndex.map(i -> events.get(i).getTime())
                    .orElseGet(() -> events.getLast().getTime());
            String messagePart = WITHOUT_INTERNET
                    + removeSeconds(events.getFirst().getTime())
                    + UNTIL
                    + removeSeconds(timeFirstErrorOrLastEvent)
                    + "\n";
            messageForUser.append(messagePart);
            messageForAdmin.append(messagePart);
        }

        processEventsSerially(messageForUser, messageForAdmin, events, deviceProcessor, firstLaunch);
    }

    private void processEventsSerially(
            StringBuilder messageForUser,
            StringBuilder messageForAdmin,
            List<Event> events,
            DeviceProcessor deviceProcessor,
            boolean firstLaunch
    ) {
        String messagePart;
        for (int i = 0; i < events.size(); i++) {
            Event currentEvent = events.get(i);
            switch (currentEvent.getEventType()) {
                case EventType.START:
                    messagePart = createMessageForStart(i, deviceProcessor, events, firstLaunch);
                    messageForUser.append(messagePart);
                    messageForAdmin.append(messagePart);
                    break;
                case EventType.ERROR:
                    messagePart = ERROR + removeSeconds(currentEvent.getTime());
                    messageForUser.append(messagePart);
                    messageForAdmin.append(messagePart)
                            .append(" :\n\"")
                            .append(currentEvent.getAdditionalInfo())
                            .append("\"");
                    return;
                case EventType.PING:
                    break;
            }
        }
    }

    private String createMessageForStart (
            int i,
            DeviceProcessor deviceProcessor,
            List<Event> events,
            boolean firstLaunch
    ) {
        Event currentEvent = events.get(i);

        if (0 == i && firstLaunch) {
            return TURN_ON + removeSeconds(currentEvent.getTime());
        }

        String beginning = 0 == i
                ? deviceProcessor.getLastOnlineTime().format(formatterWithoutSeconds)
                : removeSeconds(events.get(i - 1).getTime());

        return NO_POWER  + beginning + UNTIL + removeSeconds(currentEvent.getTime());
    }

    private Optional<Integer> getFirstIndexError(List<Event> events) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getEventType() == EventType.ERROR) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private String removeSeconds(String time) {
        return LocalDateTime.parse(time, FormattersKt.dateTimeFormatter).format(formatterWithoutSeconds);
    }

}
