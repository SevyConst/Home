package org.example.server.service;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import model.Event;
import model.EventRequest;
import model.EventType;
import org.example.ConstantsKt;
import org.example.FormattersKt;
import org.example.server.exception.DeviceChatsNotFoundException;
import org.example.server.exception.DeviceNotFoundException;
import org.example.server.model.dto.Chat;
import org.example.server.model.dto.DeviceInfo;
import org.example.server.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    @Getter
    @Setter
    private static volatile Long periodMilliseconds = ConstantsKt.PING_PERIOD_MILLISECONDS;

    public static final DateTimeFormatter formatterWithoutSeconds = DateTimeFormatter.ofPattern("HH:mm yyyy-MM-dd");
    public static final DateTimeFormatter formatterOnlyTimeWithoutSeconds = DateTimeFormatter.ofPattern("HH:mm");

    public static final String ADDED = " добавлен";
    public static final String NEW_PARAGRAPH = "\n\n";
    public static final String DASH = " - ";
    public static final String TURN_ON = "только что включился";
    public static final String NO_POWER = "не было питания между ";
    public static final String AND = " и ";
    public static final String NO_INTERNET = "не было интернета между ";
    public static final String NO_INTERNET_SINCE_FIRST_START = "не было интернета с момента включения";
    public static final String UNTIL = " до ";
    public static final String AT = " в ";
    public static final String ERROR_MONITORING = " - ошибка. Наблюдение за ";
    public static final String PAUSED = " приостановлено";
    public static final String COUNT_RESTARTS = " - за это время питание отключалось ";
    public static final String TIMES = " раз";
    public static final String NO_POWER_LESS_TWO_MINUTES = " - возможно отключение питания было очень кратковременным, но это неточно. При этом точно, что отключение питания было не больше двух минут";

    private final TelegramNotifier telegramNotifier;
    private final DeviceRepository deviceRepository;
    private final Clock clock;

    private final ConcurrentHashMap<String, DeviceProcessor> deviceIdToDeviceProcessorMap = new ConcurrentHashMap<>();

    public void processEvents(EventRequest request) {

        String deviceId = request.getDeviceId();

        while (true) {
            DeviceProcessor deviceProcessor = deviceIdToDeviceProcessorMap.computeIfAbsent(
                    deviceId,
                    id -> new DeviceProcessor(id, telegramNotifier, deviceRepository));

            deviceProcessor.getLock().lock();
            try {
                if (deviceProcessor.isRemoved()) {
                    continue;
                }

                Optional<DeviceInfo> deviceInfoOptional = deviceRepository.getDeviceInfo(deviceId);
                if (deviceInfoOptional.isEmpty()) {
                    deviceProcessor.setRemoved(true);
                    deviceIdToDeviceProcessorMap.remove(deviceId, deviceProcessor);
                    deviceProcessor.shutdown();
                    throw new DeviceNotFoundException(deviceId);
                }

                List<Chat> chatsList = deviceRepository.getDeviceChats(deviceId);
                if (chatsList.isEmpty()) {
                    throw new DeviceChatsNotFoundException("server error: can't find chats for device " + deviceId
                            + " (but the device has been found)");
                }

                boolean isFirstRequest = !deviceProcessor.isFirstRequestProcessed();
                processEvents(deviceProcessor, request, isFirstRequest, chatsList, deviceInfoOptional.get());
                deviceProcessor.setFirstRequestProcessed(true);
                return;
            } finally {
                deviceProcessor.getLock().unlock();
            }
        }
    }

    private void processEvents(
            DeviceProcessor deviceProcessor,
            EventRequest request,
            boolean isFirstRequest,
            List<Chat> chatsList,
            DeviceInfo deviceInfo
    ) {
        String deviceId = request.getDeviceId();

        if (deviceInfo.hasError()) {
            return;
        }

        boolean deviceHasClock = deviceInfo.hasClock();

        List<Event> events = request.getEvents();

        Optional<Integer> firstErrorIndex = getFirstErrorIndex(events);
        if (firstErrorIndex.isPresent()) {
            deviceRepository.updateHasError(deviceId, true);
            TextPair textPair = processError(request, firstErrorIndex.get());
            sendMessages(textPair, chatsList, deviceProcessor.getChatIdToRepliedMessageIdMap());
            return;
        }

        TextPair textPair = 1 == events.size()
                ? buildTextPairForTheOneEvent(request, deviceProcessor, isFirstRequest, deviceHasClock)
                : buildTextPairForTheMultipleEvents(request, deviceProcessor, isFirstRequest, deviceHasClock);

        Optional<String> textForAdmin = textPair.getTextForAdminOptional();
        if (isFirstRequest) {
            textForAdmin = textForAdmin.isEmpty()
                    ? Optional.of(deviceId + ADDED)
                    : Optional.of(textForAdmin.get() + NEW_PARAGRAPH + deviceId + ADDED);
            textPair.setTextForAdminOptional(textForAdmin);
        }

        sendMessages(textPair, chatsList, deviceProcessor.getChatIdToRepliedMessageIdMap());

        ZonedDateTime lastOnlineTime = deviceHasClock
                ? eventTime(events.getLast())
                : ZonedDateTime.now(clock);
        deviceProcessor.setLastOnlineTime(lastOnlineTime);
        deviceProcessor.setOffline(false);
        deviceProcessor.startCountdownTimer();
    }

    // The device sends an instant with its own offset; messages must show it in the display zone.
    // Converting the instant, rather than relabelling the local fields, is what keeps this time
    // comparable with ZonedDateTime.now(clock) elsewhere in this class.
    private ZonedDateTime eventTime(Event event) {
        return OffsetDateTime.parse(event.getTime(), FormattersKt.dateTimeFormatter)
                .atZoneSameInstant(clock.getZone());
    }

    @Data
    private static class TextPair {
        private Optional<String> textForAdminOptional;
        private Optional<String> textForUserOptional;
    }

    private Optional<Integer> getFirstErrorIndex(List<Event> events) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getEventType() == EventType.ERROR) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private TextPair processError(EventRequest request, int firstErrorIndex) {
        String textForUser = request.getDeviceId() + ERROR_MONITORING + request.getDeviceId() + PAUSED;
        TextPair textPair = new TextPair();
        textPair.setTextForUserOptional(Optional.of(textForUser));
        textPair.setTextForAdminOptional(Optional.of(
                textForUser
                        + "\n"
                        + request.getEvents().get(firstErrorIndex).getAdditionalInfo()));
        return textPair;
    }

    private TextPair buildTextPairForTheOneEvent(
            EventRequest request,
            DeviceProcessor deviceProcessor,
            boolean isFirstRequest,
            boolean deviceHasClock
    ) {
        if (isFirstRequest) {
            return buildTextPairForTheOneFirstEvent(request);
        } else if (deviceProcessor.isOffline()) {
            return buildTextPairForTheOneEventIfOffline(request, deviceProcessor.getLastOnlineTime(), deviceHasClock);
        }

        return buildTextPairForTheOneEventIfOnline(request, deviceProcessor.getLastOnlineTime(), deviceHasClock);
    }

    private TextPair buildTextPairForTheOneFirstEvent(EventRequest request) {
        EventType eventType = request.getEvents().getFirst().getEventType();
        Optional<String> result = EventType.START == eventType
                ? Optional.of(request.getDeviceId() + " " + TURN_ON)
                : Optional.empty();

        TextPair textPair = new TextPair();
        textPair.setTextForAdminOptional(result);
        textPair.setTextForUserOptional(result);

        return textPair;
    }

    private TextPair buildTextPairForTheOneEventIfOffline(
            EventRequest request,
            ZonedDateTime lastOnlineTime,
            boolean deviceHasClock
    ) {
        Event event = request.getEvents().getFirst();
        ZonedDateTime eventSendingTime = eventTime(event);

        ZonedDateTime eventReceivingTime = ZonedDateTime.now(clock);
        String deviceId = request.getDeviceId();
        return switch (event.getEventType()) {
            case EventType.PING -> buildTextPairForTheOnePingEventIfOffline(
                    deviceId,
                    lastOnlineTime,
                    deviceHasClock ? eventSendingTime : eventReceivingTime);
            case EventType.START -> buildTextPairForTheOneStartEvent(
                    deviceId,
                    lastOnlineTime,
                    deviceHasClock ? eventSendingTime : eventReceivingTime);

            default -> {
                TextPair textPair = new TextPair();
                textPair.setTextForAdminOptional(Optional.empty());
                textPair.setTextForUserOptional(Optional.empty());

                yield textPair;
            }
        };
    }

    private TextPair buildTextPairForTheOneEventIfOnline(
            EventRequest request,
            ZonedDateTime lastOnlineTime,
            boolean deviceHasClock
    ) {
        Event event = request.getEvents().getFirst();
        if (event.getEventType() != EventType.START) {
            TextPair textPair = new TextPair();
            textPair.setTextForAdminOptional(Optional.empty());
            textPair.setTextForUserOptional(Optional.empty());

            return textPair;
        }

        ZonedDateTime eventSendingTime = eventTime(event);

        ZonedDateTime eventReceivingTime = ZonedDateTime.now(clock);
        return buildTextPairForTheOneStartEvent(
                request.getDeviceId(),
                lastOnlineTime,
                deviceHasClock ? eventSendingTime : eventReceivingTime);
    }

    private TextPair buildTextPairForTheMultipleEvents(
            EventRequest request,
            DeviceProcessor deviceProcessor,
            boolean isFirstRequest,
            boolean deviceHasClock
    ) {
        if (!deviceHasClock) {
            return buildTextForMultipleEventsFromDeviceWithoutClock(
                    request,
                    deviceProcessor,
                    isFirstRequest
            );
        }

        ZonedDateTime timeFirstEvent = eventTime(request.getEvents().getFirst());

        ZonedDateTime beginning = isFirstRequest
                ? timeFirstEvent
                : deviceProcessor.getLastOnlineTime();

        ZonedDateTime end = eventTime(request.getEvents().getLast());

        boolean isTheOneDay = beginning.toLocalDate().isEqual(end.toLocalDate());

        StringBuilder result = new StringBuilder(buildHeader(
                request,
                beginning,
                end,
                isTheOneDay,
                isFirstRequest
        ));

        List<Event> events = request.getEvents();
        boolean linesAdded = false;
        for (int i = 0; i < events.size(); i++) {
            Event currentEvent = events.get(i);
            if (EventType.START == currentEvent.getEventType()) {
                Optional<String> lineTextOptional = buildTextForStart(
                        i,
                        events,
                        beginning,
                        isTheOneDay,
                        isFirstRequest
                );
                if (lineTextOptional.isPresent()) {
                    if (!linesAdded) {
                        linesAdded = true;
                        result.append("\n");
                    }
                    result.append(lineTextOptional.get());
                }
            }
        }

        Optional<String> resultStringOptional = Optional.of(result.toString());

        TextPair textPair = new TextPair();
        textPair.setTextForAdminOptional(resultStringOptional);
        textPair.setTextForUserOptional(resultStringOptional);

        return textPair;
    }

    private StringBuilder buildHeader(
            EventRequest request,
            ZonedDateTime beginning,
            ZonedDateTime end,
            boolean isTheOneDay,
            boolean isFirstRequest

    ){
        StringBuilder result = new StringBuilder(request.getDeviceId())
                .append(":\n");

        DateTimeFormatter formatter = isTheOneDay
                ? formatterOnlyTimeWithoutSeconds
                : formatterWithoutSeconds;

        if (isFirstRequest) {
            return result.append(NO_INTERNET_SINCE_FIRST_START)
                    .append(AT)
                    .append(formatter.format(beginning))
                    .append(UNTIL)
                    .append(formatter.format(end));
        }

        return result.append(NO_INTERNET)
                .append(formatter.format(beginning))
                .append(AND)
                .append(formatter.format(end));
    }

    private TextPair buildTextForMultipleEventsFromDeviceWithoutClock(
            EventRequest request,
            DeviceProcessor deviceProcessor,
            boolean isFirstRequest
    ) {
        StringBuilder result = new StringBuilder(request.getDeviceId())
                .append(DASH);
        if (isFirstRequest) {
            result.append(NO_INTERNET_SINCE_FIRST_START)
                    .append(UNTIL)
                    .append(ZonedDateTime.now(clock).format(formatterOnlyTimeWithoutSeconds));
        } else {

            ZonedDateTime lastOnlineTime = deviceProcessor.getLastOnlineTime();
            ZonedDateTime nowTime = ZonedDateTime.now(clock);

            DateTimeFormatter formatter = lastOnlineTime.toLocalDate().isEqual(nowTime.toLocalDate())
                    ? formatterOnlyTimeWithoutSeconds
                    : formatterWithoutSeconds;

            result.append(NO_INTERNET)
                    .append(formatter.format(lastOnlineTime))
                    .append(AND)
                    .append(formatter.format(nowTime));

        }
        int nRestarts = countRestarts(request.getEvents(), isFirstRequest);
        if (0 != nRestarts) {
            result.append(COUNT_RESTARTS)
                    .append(nRestarts)
                    .append(TIMES);

            int end = nRestarts & 7;
            if (2 == end || 3 == end || 4 == end) {
                result.append("а");
            }
        }
        Optional<String> resultStringOptional = Optional.of(result.toString());
        TextPair textPair = new TextPair();
        textPair.setTextForAdminOptional(resultStringOptional);
        textPair.setTextForUserOptional(resultStringOptional);

        return textPair;
    }

    private Optional<String> buildTextForStart(
            int i,
            List<Event> events,
            ZonedDateTime beginning,
            boolean isTheOneDay,
            boolean isFirstRequest
    ) {

        if (0 == i) {
            if (isFirstRequest) {
                return Optional.empty();
            }
        } else {
            beginning = eventTime(events.get(i - 1));
        }

        ZonedDateTime end = eventTime(events.get(i));

        DateTimeFormatter formatter = isTheOneDay
                ? formatterOnlyTimeWithoutSeconds
                : formatterWithoutSeconds;

        String timeBeginningString = formatter.format(beginning);
        String timeEndString = formatter.format(end);

        return Optional.of(
                "\n"
                        + NO_POWER
                        + timeBeginningString
                        + AND
                        + timeEndString
                        + buildSuffixIfPauseIsShort(timeBeginningString, timeEndString, isTheOneDay)
        );
    }

    private int countRestarts(List<Event> events, boolean isFirstRequest) {
        int result = (int) events.stream()
                .filter(event -> EventType.START.equals(event.getEventType()))
                .count();
        if (EventType.START == events.getFirst().getEventType() && isFirstRequest) {
            result--;
        }
        return result;
    }

    private String buildSuffixIfPauseIsShort(
            String timeBeginningString,
            String timeEndString,
            boolean isTheOneDay
    ) {
        if (isTheOneDay) {
            LocalTime timeBeginning = LocalTime.parse(timeBeginningString, formatterOnlyTimeWithoutSeconds);
            LocalTime timeEnd = LocalTime.parse(timeEndString, formatterOnlyTimeWithoutSeconds);
            // Measure the gap instead of shifting the start: LocalTime is cyclic, so 23:59 plus one
            // minute is 00:00 — smaller than the value it started from — and an outage contained in
            // the last minute of a day would lose its suffix.
            if (Duration.between(timeBeginning, timeEnd).toMinutes() <= 1) {
                return NO_POWER_LESS_TWO_MINUTES;
            }
            return "";
        } else {
            LocalDateTime timeBeginning = LocalDateTime.parse(timeBeginningString, formatterWithoutSeconds);
            LocalDateTime timeEnd = LocalDateTime.parse(timeEndString, formatterWithoutSeconds);
            if (timeBeginning.plusMinutes(1).isAfter(timeEnd)
                    || timeBeginning.plusMinutes(1).isEqual(timeEnd)) {
                return NO_POWER_LESS_TWO_MINUTES;
            }
            return "";
        }
    }

    private TextPair buildTextPairForTheOnePingEventIfOffline(
            String deviceId,
            ZonedDateTime beginning,
            ZonedDateTime end
    ) {
        StringBuilder result = new StringBuilder(deviceId)
                .append(DASH)
                .append(NO_INTERNET);

        DateTimeFormatter formatter = beginning.toLocalDate().isEqual(end.toLocalDate())
                ? formatterOnlyTimeWithoutSeconds
                : formatterWithoutSeconds;

        result.append(formatter.format(beginning))
                .append(AND)
                .append(formatter.format(end));

        Optional<String> resultStringOptional = Optional.of(result.toString());
        TextPair textPair = new TextPair();
        textPair.setTextForAdminOptional(resultStringOptional);
        textPair.setTextForUserOptional(resultStringOptional);

        return textPair;

    }

    private TextPair buildTextPairForTheOneStartEvent(
            String deviceId,
            ZonedDateTime beginning,
            ZonedDateTime end
    ) {
        StringBuilder result = new StringBuilder(deviceId)
                .append(DASH)
                .append(NO_POWER);

        boolean isTheOneDay = beginning.toLocalDate().isEqual(end.toLocalDate());
        DateTimeFormatter formatter = isTheOneDay
                ? formatterOnlyTimeWithoutSeconds
                : formatterWithoutSeconds;

        String timeBeginningString = formatter.format(beginning);
        String timeEndString = formatter.format(end);

        result.append(timeBeginningString)
                .append(AND)
                .append(timeEndString)
                .append(buildSuffixIfPauseIsShort(timeBeginningString, timeEndString, isTheOneDay));

        Optional<String> resultStringOptional = Optional.of(result.toString());
        TextPair textPair = new TextPair();
        textPair.setTextForAdminOptional(resultStringOptional);
        textPair.setTextForUserOptional(resultStringOptional);

        return textPair;
    }

    private void sendMessages(
            TextPair textPair,
            List<Chat> chatsList,
            Map<Long, Integer> chatIdToRepliedMessageIdMap
    ) {
        for (Chat chat : chatsList) {
            Long chatId = chat.chatId();
            Integer repliedMessageId = chatIdToRepliedMessageIdMap.get(chatId);
            if (null != repliedMessageId) {
                chatIdToRepliedMessageIdMap.put(chatId, null);
            }
            Optional<String> textForAdminOptional = textPair.getTextForAdminOptional();
            if (chat.isAdmin() && textForAdminOptional.isPresent()) {
                try {
                    telegramNotifier.send(textForAdminOptional.get(), chatId, Optional.ofNullable(repliedMessageId));
                } catch (TelegramApiException e) {
                    log.error(
                            "Failed to send text to Telegram '{}' to admin {}",
                            textForAdminOptional.get(),
                            chatId,
                            e
                    );
                }
            }
            Optional<String> textForUserOptional = textPair.getTextForUserOptional();
            if (!chat.isAdmin() && textForUserOptional.isPresent()) {
                try {
                    telegramNotifier.send(textForUserOptional.get(), chatId, Optional.ofNullable(repliedMessageId));
                } catch (TelegramApiException e) {
                    log.error(
                            "Failed to send text to Telegram '{}' to user {}",
                            textForUserOptional.get(),
                            chatId,
                            e
                    );
                }
            }
        }
    }
}
