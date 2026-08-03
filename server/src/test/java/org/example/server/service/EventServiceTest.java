package org.example.server.service;

import model.Event;
import model.EventRequest;
import model.EventType;
import org.example.FormattersKt;
import org.example.server.exception.DeviceNotFoundException;
import org.example.server.model.dto.Chat;
import org.example.server.model.dto.DeviceInfo;
import org.example.server.repository.DeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class EventServiceTest {

    private static final String DEVICE_ID = "test_device_id";
    private static final long CHAT_ID_ADMIN = 10L;
    private static final long CHAT_ID_USER = 11L;
    private static final int MESSAGE_ID_ADMIN = 100500;
    private static final int MESSAGE_ID_USER = 100600;
    private static final long PERIOD_MILLISECONDS = 100L;
    private static final double COEFFICIENT_SLEEP = 1.1;
    // The zone messages are rendered in. Pinned, so the suite does not depend on the host timezone.
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Moscow");
    private static final ZonedDateTime INIT_TIME =
            Instant.parse("2026-07-06T13:56:00Z").atZone(DISPLAY_ZONE);
    private static final ZonedDateTime ANOTHER_DAY =
            Instant.parse("2026-07-08T13:56:00Z").atZone(DISPLAY_ZONE);

    private final TelegramNotifier telegramNotifierMock = Mockito.mock(TelegramNotifier.class);
    private final DeviceRepository deviceRepositoryMock = Mockito.mock(DeviceRepository.class);
    private final Clock clockMock = Mockito.mock(Clock.class);

    private Long periodMillisecondsBeforeTest;

    @BeforeEach
    void savePeriodMilliseconds() {
        periodMillisecondsBeforeTest = EventService.getPeriodMilliseconds();
    }

    @AfterEach
    void restorePeriodMilliseconds() {
        EventService.setPeriodMilliseconds(periodMillisecondsBeforeTest);
    }

    @Test
    void pingRequestPingRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID)).thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(INIT_TIME.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(INIT_TIME.getZone());

        eventService.processEvents(eventRequest1);

        ZonedDateTime timeRequest2 = INIT_TIME.plusMinutes(1);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(timeRequest2),
                        null
                )),
                DEVICE_ID
        );

        Mockito.when(clockMock.instant()).thenReturn(timeRequest2.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeRequest2.getZone());

        eventService.processEvents(eventRequest2);

        Mockito.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void pingRequestPingRequestFromDeviceWithoutClock() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID)).thenReturn(Optional.of(new DeviceInfo(false, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(INIT_TIME.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(INIT_TIME.getZone());

        eventService.processEvents(eventRequest1);

        ZonedDateTime timeRequest2 = INIT_TIME.plusMinutes(1);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(timeRequest2),
                        null
                )),
                DEVICE_ID
        );

        eventService.processEvents(eventRequest2);

        Mockito.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void doublePingOneRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        ZonedDateTime timeSending = INIT_TIME.plusMinutes(1);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(INIT_TIME),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(timeSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeReceiving = timeSending.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID)).thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeReceiving.getZone());

        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.AT
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.UNTIL
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSending)
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        Mockito.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.AT
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.UNTIL
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSending),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    // The devices sit in Moscow, the server abroad, and the client OS may be left on UTC.
    // Whatever offset arrives, the message must carry Moscow wall clock and no zone marker.
    // Expected times are literals on purpose: deriving them from a formatter would assert nothing.
    @Test
    void messageShowsDisplayZoneTimeWhenDeviceSendsUtcOffset() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        Instant firstPing = Instant.parse("2026-07-06T13:56:00Z");
        Instant secondPing = Instant.parse("2026-07-06T14:56:00Z");

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(firstPing.atOffset(ZoneOffset.UTC)),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(secondPing.atOffset(ZoneOffset.UTC)),
                                null
                        )
                ),
                DEVICE_ID
        );

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID)).thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(new Chat(CHAT_ID_USER, false)));
        Mockito.when(clockMock.instant()).thenReturn(secondPing);
        Mockito.when(clockMock.getZone()).thenReturn(DISPLAY_ZONE);

        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.AT
                                + "16:56"
                                + EventService.UNTIL
                                + "17:56",
                        CHAT_ID_USER,
                        Optional.empty()
                );

        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void doublePingOneRequestFromDeviceWithoutClock() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        ZonedDateTime timeSending = INIT_TIME.plusMinutes(1);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(INIT_TIME),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(timeSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeReceiving = timeSending.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID)).thenReturn(Optional.of(new DeviceInfo(false, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeReceiving.getZone());

        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.UNTIL
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeReceiving)
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        Mockito.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.UNTIL
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeReceiving),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void doublePingOneRequestDifferentDays() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(INIT_TIME),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(ANOTHER_DAY),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeReceiving = ANOTHER_DAY.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID)).thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeReceiving.getZone());

        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID +
                                ":\n"
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.AT
                                + EventService.formatterWithoutSeconds.format(INIT_TIME)
                                + EventService.UNTIL
                                + EventService.formatterWithoutSeconds.format(ANOTHER_DAY)
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        Mockito.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.AT
                                + EventService.formatterWithoutSeconds.format(INIT_TIME)
                                + EventService.UNTIL
                                + EventService.formatterWithoutSeconds.format(ANOTHER_DAY),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void pingRequestPausePingRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest);
        ZonedDateTime timeMessagePauseSentToTelegram = timeFirstRequestReceiving.plusMinutes(1);
        Mockito.when(clockMock.instant()).thenReturn(timeMessagePauseSentToTelegram.toInstant());
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_ADMIN,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_ADMIN);
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_USER,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_USER);

        sleepUntilOffline();

        ZonedDateTime timeSecondRequestSending = timeMessagePauseSentToTelegram.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME) + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending),
                        CHAT_ID_ADMIN,
                        Optional.of(MESSAGE_ID_ADMIN)
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending),
                        CHAT_ID_USER,
                        Optional.of(MESSAGE_ID_USER)
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestPausePingRequestFromDeviceWithoutClock() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(false, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest);
        ZonedDateTime timeMessagePauseSentToTelegram = timeFirstRequestReceiving.plusMinutes(1);
        Mockito.when(clockMock.instant()).thenReturn(timeMessagePauseSentToTelegram.toInstant());
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_ADMIN,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_ADMIN);
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_USER,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_USER);

        sleepUntilOffline();

        ZonedDateTime timeSecondRequestSending = timeMessagePauseSentToTelegram.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestReceiving),
                        CHAT_ID_ADMIN,
                        Optional.of(MESSAGE_ID_ADMIN)
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestReceiving),
                        CHAT_ID_USER,
                        Optional.of(MESSAGE_ID_USER)
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestPausePingRequestDifferentDays() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest);
        Mockito.when(clockMock.instant()).thenReturn(ANOTHER_DAY.toInstant());
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_ADMIN,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_ADMIN);
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_USER,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_USER);

        sleepUntilOffline();

        ZonedDateTime timeSecondRequestSending = ANOTHER_DAY.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET
                                + EventService.formatterWithoutSeconds.format(INIT_TIME) + EventService.AND
                                + EventService.formatterWithoutSeconds.format(timeSecondRequestSending),
                        CHAT_ID_ADMIN,
                        Optional.of(MESSAGE_ID_ADMIN)
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET
                                + EventService.formatterWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterWithoutSeconds.format(timeSecondRequestSending),
                        CHAT_ID_USER,
                        Optional.of(MESSAGE_ID_USER)
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestPausePingRequestFromDeviceWithoutClockDifferentDays() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(false, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest);
        Mockito.when(clockMock.instant()).thenReturn(ANOTHER_DAY.toInstant());
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_ADMIN,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_ADMIN);
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_USER,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_USER);

        sleepUntilOffline();

        ZonedDateTime timeSecondRequestSending = ANOTHER_DAY.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET
                                + EventService.formatterWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterWithoutSeconds.format(timeSecondRequestReceiving),
                        CHAT_ID_ADMIN,
                        Optional.of(MESSAGE_ID_ADMIN)
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET
                                + EventService.formatterWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterWithoutSeconds.format(timeSecondRequestReceiving),
                        CHAT_ID_USER,
                        Optional.of(MESSAGE_ID_USER)
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestStartRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);

        ZonedDateTime timeSecondRequestSending = timeFirstRequestReceiving.plusMinutes(1);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);
        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeSecondRequestReceiving.getZone());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NO_POWER_LESS_TWO_MINUTES,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NO_POWER_LESS_TWO_MINUTES,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestStartRequestFromDeviceWithoutClock() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(false, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);

        ZonedDateTime timeSecondRequestSending = timeFirstRequestReceiving.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeSecondRequestReceiving.getZone());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestReceiving)
                                + EventService.NO_POWER_LESS_TWO_MINUTES,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestReceiving)
                                + EventService.NO_POWER_LESS_TWO_MINUTES,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestStartRequestDifferentDays() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(ANOTHER_DAY),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = ANOTHER_DAY.plusMinutes(1);
        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeSecondRequestReceiving.getZone());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterWithoutSeconds.format(ANOTHER_DAY),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterWithoutSeconds.format(ANOTHER_DAY),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestStartRequestFromDeviceWithoutClockDifferentDays() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(false, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(ANOTHER_DAY),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = ANOTHER_DAY.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeSecondRequestReceiving.getZone());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterWithoutSeconds.format(timeSecondRequestReceiving),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterWithoutSeconds.format(timeSecondRequestReceiving),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestPauseStartRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);
        ZonedDateTime timeMessagePauseSentToTelegram = timeFirstRequestReceiving.plusMinutes(1);
        Mockito.when(clockMock.instant()).thenReturn(timeMessagePauseSentToTelegram.toInstant());
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_ADMIN,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_ADMIN);
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_USER,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_USER);

        sleepUntilOffline();

        ZonedDateTime timeSecondRequestSending = timeMessagePauseSentToTelegram.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending),
                        CHAT_ID_ADMIN,
                        Optional.of(MESSAGE_ID_ADMIN)
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending),
                        CHAT_ID_USER,
                        Optional.of(MESSAGE_ID_USER)
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestPauseStartRequestFromDeviceWithoutClock() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        EventService.formatterWithoutSeconds.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(false, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);
        ZonedDateTime timeMessagePauseSentToTelegram = timeFirstRequestReceiving.plusMinutes(1);
        Mockito.when(clockMock.instant()).thenReturn(timeMessagePauseSentToTelegram.toInstant());
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_ADMIN,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_ADMIN);
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_USER,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_USER);

        sleepUntilOffline();

        ZonedDateTime timeSecondRequestSending = timeMessagePauseSentToTelegram.plusMinutes(1);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestReceiving),
                        CHAT_ID_ADMIN,
                        Optional.of(MESSAGE_ID_ADMIN)
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestReceiving),
                        CHAT_ID_USER,
                        Optional.of(MESSAGE_ID_USER)
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void startRequestStartRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);

        ZonedDateTime timeSecondRequestSending = timeFirstRequestReceiving.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + " "
                                + EventService.TURN_ON
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NO_POWER_LESS_TWO_MINUTES,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NO_POWER_LESS_TWO_MINUTES,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void startRequestStartRequestFromDeviceWithoutClock() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(false, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);

        ZonedDateTime timeSecondRequestSending = timeFirstRequestReceiving.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + " "
                                + EventService.TURN_ON
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestReceiving)
                                + EventService.NO_POWER_LESS_TWO_MINUTES,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestReceiving)
                                + EventService.NO_POWER_LESS_TWO_MINUTES,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void doubleStartOneRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        ZonedDateTime timeRequestSending = INIT_TIME.plusMinutes(1);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(INIT_TIME),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeRequestSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeRequestReceiving.getZone());

        eventService.processEvents(eventRequest);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.AT
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.UNTIL
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeRequestSending)
                                + EventService.NEW_PARAGRAPH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeRequestSending)
                                + EventService.NO_POWER_LESS_ONE_MINUTE
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.AT
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.UNTIL
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeRequestSending)
                                + EventService.NEW_PARAGRAPH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeRequestSending)
                                + EventService.NO_POWER_LESS_ONE_MINUTE,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void doubleStartOneRequestFromDeviceWithoutClock() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        ZonedDateTime timeRequestSending = INIT_TIME.plusMinutes(1);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(INIT_TIME),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeRequestSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(false, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeRequestReceiving.getZone());

        eventService.processEvents(eventRequest);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.UNTIL
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeRequestSending)
                                + EventService.COUNT_RESTARTS
                                + 1
                                + EventService.TIMES
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.UNTIL
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeRequestSending)
                                + EventService.COUNT_RESTARTS
                                + 1
                                + EventService.TIMES,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void startRequestPauseStartStartRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest);
        ZonedDateTime timeMessagePauseSentToTelegram = timeFirstRequestReceiving.plusMinutes(1);
        Mockito.when(clockMock.instant()).thenReturn(timeMessagePauseSentToTelegram.toInstant());
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_ADMIN,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_ADMIN);
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_USER,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_USER);

        sleepUntilOffline();

        ZonedDateTime timeSecondRequestFirstEvent = timeMessagePauseSentToTelegram.plusMinutes(1);
        ZonedDateTime timeSecondRequestSending = timeSecondRequestFirstEvent.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestFirstEvent),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + " "
                                + EventService.TURN_ON
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + " "
                                + EventService.TURN_ON,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NEW_PARAGRAPH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestFirstEvent)
                                + "\n"
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestFirstEvent)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NO_POWER_LESS_ONE_MINUTE,
                        CHAT_ID_ADMIN,
                        Optional.of(MESSAGE_ID_ADMIN)
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NEW_PARAGRAPH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestFirstEvent)
                                + "\n"
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestFirstEvent)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NO_POWER_LESS_ONE_MINUTE,
                        CHAT_ID_USER,
                        Optional.of(MESSAGE_ID_USER)
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void startRequestPauseStartStartRequestFromDeviceWithoutClock() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(false, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest);
        ZonedDateTime timeMessagePauseSentToTelegram = timeFirstRequestReceiving.plusMinutes(1);
        Mockito.when(clockMock.instant()).thenReturn(timeMessagePauseSentToTelegram.toInstant());
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_ADMIN,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_ADMIN);
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_USER,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_USER);

        sleepUntilOffline();

        ZonedDateTime timeSecondRequestFirstEvent = timeMessagePauseSentToTelegram.plusMinutes(1);
        ZonedDateTime timeSecondRequestSending = timeSecondRequestFirstEvent.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestFirstEvent),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestReceiving)
                                + EventService.COUNT_RESTARTS
                                + 2
                                + EventService.TIMES
                                + "а",
                        CHAT_ID_ADMIN,
                        Optional.of(MESSAGE_ID_ADMIN)
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestReceiving)
                                + EventService.COUNT_RESTARTS
                                + 2
                                + EventService.TIMES
                                + "а",
                        CHAT_ID_USER,
                        Optional.of(MESSAGE_ID_USER)
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void startRequestStartStartRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);

        ZonedDateTime timeSecondRequestFirstEvent = timeFirstRequestReceiving.plusMinutes(1);
        ZonedDateTime timeSecondRequestSending = timeSecondRequestFirstEvent.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestFirstEvent),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NEW_PARAGRAPH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestFirstEvent)
                                + EventService.NO_POWER_LESS_TWO_MINUTES
                                + "\n"
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestFirstEvent)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NO_POWER_LESS_ONE_MINUTE,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NEW_PARAGRAPH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestFirstEvent)
                                + EventService.NO_POWER_LESS_TWO_MINUTES
                                + "\n"
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestFirstEvent)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NO_POWER_LESS_ONE_MINUTE,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void startRequestStartStartRequestFromDeviceWithoutClock() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(false, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);

        ZonedDateTime timeSecondRequestFirstEvent = timeFirstRequestReceiving.plusMinutes(1);
        ZonedDateTime timeSecondRequestSending = timeSecondRequestFirstEvent.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestFirstEvent),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestReceiving)
                                + EventService.COUNT_RESTARTS
                                + 2
                                + EventService.TIMES
                                + "а",
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestReceiving)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestReceiving)
                                + EventService.COUNT_RESTARTS
                                + 2
                                + EventService.TIMES
                                + "а",
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void startRequestPauseStartRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null)
                ),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest);
        ZonedDateTime timeMessagePauseSentToTelegram = timeFirstRequestReceiving.plusMinutes(1);
        Mockito.when(clockMock.instant()).thenReturn(timeMessagePauseSentToTelegram.toInstant());
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_ADMIN,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_ADMIN);
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_USER,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_USER);

        sleepUntilOffline();

        ZonedDateTime timeSecondRequestSending = timeMessagePauseSentToTelegram.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                        null)
                ),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending),
                        CHAT_ID_ADMIN,
                        Optional.of(MESSAGE_ID_ADMIN)
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending),
                        CHAT_ID_USER,
                        Optional.of(MESSAGE_ID_USER)
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void startRequestPingRequestStartRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);

        ZonedDateTime timeSecondRequestSending = timeFirstRequestReceiving.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                        null)
                ),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        ZonedDateTime timeThirdRequestSending = timeFirstRequestReceiving.plusMinutes(1);

        EventRequest eventRequest3 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(timeThirdRequestSending),
                        null)
                ),
                DEVICE_ID
        );

        ZonedDateTime timeThirdRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeThirdRequestReceiving.toInstant());

        eventService.processEvents(eventRequest3);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeThirdRequestSending)
                                + EventService.NO_POWER_LESS_ONE_MINUTE,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeThirdRequestSending)
                                + EventService.NO_POWER_LESS_ONE_MINUTE,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void startPingStartRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        ZonedDateTime timeSecondEvent = INIT_TIME.plusMinutes(1L);
        ZonedDateTime timeSending = timeSecondEvent.plusMinutes(1L);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(INIT_TIME),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(timeSecondEvent),
                                null
                        ),
                        new Event(
                                3L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeReceiving = timeSending.plusMinutes(1L);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeReceiving.getZone());

        eventService.processEvents(eventRequest);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.AT
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.UNTIL
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSending)
                                + EventService.NEW_PARAGRAPH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondEvent)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSending)
                                + EventService.NO_POWER_LESS_ONE_MINUTE
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.AT
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.UNTIL
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSending)
                                + EventService.NEW_PARAGRAPH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondEvent)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSending)
                                + EventService.NO_POWER_LESS_ONE_MINUTE,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void startRequestPingStartRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);

        ZonedDateTime timeSecondRequestFirstEvent = timeFirstRequestReceiving.plusMinutes(1);
        ZonedDateTime timeSecondRequestSending = timeSecondRequestFirstEvent.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestFirstEvent),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NEW_PARAGRAPH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestFirstEvent)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NO_POWER_LESS_ONE_MINUTE,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NEW_PARAGRAPH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestFirstEvent)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NO_POWER_LESS_ONE_MINUTE,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    /**
     * Two requests
     */
    @Test
    void startPingRequestStartRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        ZonedDateTime timeFirstRequestSending = INIT_TIME.plusMinutes(1);

        EventRequest eventRequest1 = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(INIT_TIME),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(timeFirstRequestSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = timeFirstRequestSending.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest1);

        ZonedDateTime timeSecondRequestSending = timeFirstRequestReceiving.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                                3L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.AT
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.UNTIL
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestSending)
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET_SINCE_FIRST_START
                                + EventService.AT
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.UNTIL
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestSending),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestSending)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NO_POWER_LESS_TWO_MINUTES,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + EventService.DASH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeFirstRequestSending)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NO_POWER_LESS_TWO_MINUTES,
                        CHAT_ID_USER,
                        Optional.empty()
                );


        inOrder.verifyNoMoreInteractions();

    }

    @Test
    void startRequestPausePingPingStartPingRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                                1L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(INIT_TIME),
                                null
                        )
                ),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest);
        ZonedDateTime timeMessagePauseSentToTelegram = timeFirstRequestReceiving.plusMinutes(1);
        Mockito.when(clockMock.instant()).thenReturn(timeMessagePauseSentToTelegram.toInstant());
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_ADMIN,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_ADMIN);
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_USER,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_USER);

        sleepUntilOffline();

        ZonedDateTime timeSecondRequestFirstEvent = timeFirstRequestReceiving.plusMinutes(1);
        ZonedDateTime timeSecondRequestSecondEvent = timeSecondRequestFirstEvent.plusMinutes(1);
        ZonedDateTime timeSecondRequestThirdEvent = timeSecondRequestSecondEvent.plusMinutes(1);
        ZonedDateTime timeSecondRequestSending = timeSecondRequestThirdEvent.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(
                        new Event(
                                2L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestFirstEvent),
                                null
                        ),
                        new Event(
                                3L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestSecondEvent),
                                null
                        ),
                        new Event(
                                4L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestThirdEvent),
                                null
                        ),
                        new Event(5L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(timeSecondRequestSending),
                                null)
                ),
                DEVICE_ID
        );

        ZonedDateTime timeSecondRequestReceiving = timeSecondRequestSending.plusMinutes(1);

        Mockito.when(clockMock.instant()).thenReturn(timeSecondRequestReceiving.toInstant());

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON
                                + EventService.NEW_PARAGRAPH
                                + DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID
                                + " "
                                + EventService.TURN_ON,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NEW_PARAGRAPH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSecondEvent)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestThirdEvent)
                                + EventService.NO_POWER_LESS_ONE_MINUTE,
                        CHAT_ID_ADMIN,
                        Optional.of(MESSAGE_ID_ADMIN)
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + ":\n"
                                + EventService.NO_INTERNET
                                + EventService.formatterOnlyTimeWithoutSeconds.format(INIT_TIME)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSending)
                                + EventService.NEW_PARAGRAPH
                                + EventService.NO_POWER
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestSecondEvent)
                                + EventService.AND
                                + EventService.formatterOnlyTimeWithoutSeconds.format(timeSecondRequestThirdEvent)
                                + EventService.NO_POWER_LESS_ONE_MINUTE,
                        CHAT_ID_USER,
                        Optional.of(MESSAGE_ID_USER)
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestErrorRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID)).thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(INIT_TIME.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(INIT_TIME.getZone());

        eventService.processEvents(eventRequest1);

        ZonedDateTime timeErrorSending = INIT_TIME.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.ERROR,
                        FormattersKt.dateTimeFormatter.format(timeErrorSending),
                        null
                )),
                DEVICE_ID
        );

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ERROR_MONITORING
                                + DEVICE_ID
                                + EventService.PAUSED
                                + "\nnull",
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ERROR_MONITORING
                                + DEVICE_ID
                                + EventService.PAUSED,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestPauseErrorRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        ZonedDateTime timeFirstRequestReceiving = INIT_TIME.plusMinutes(1);

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID))
                .thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(timeFirstRequestReceiving.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(timeFirstRequestReceiving.getZone());

        eventService.processEvents(eventRequest);
        ZonedDateTime timeMessagePauseSentToTelegram = timeFirstRequestReceiving.plusMinutes(1);
        Mockito.when(clockMock.instant()).thenReturn(timeMessagePauseSentToTelegram.toInstant());
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_ADMIN,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_ADMIN);
        Mockito.when(telegramNotifierMock.send(
                String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                CHAT_ID_USER,
                Optional.empty()
        )).thenReturn(MESSAGE_ID_USER);

        sleepUntilOffline();

        ZonedDateTime timeErrorSending = timeMessagePauseSentToTelegram.plusMinutes(1);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.ERROR,
                        FormattersKt.dateTimeFormatter.format(timeErrorSending),
                        null
                )),
                DEVICE_ID
        );

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        String.format(DeviceProcessor.OFFLINE, DEVICE_ID, DEVICE_ID, DEVICE_ID),
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ERROR_MONITORING
                                + DEVICE_ID
                                + EventService.PAUSED
                                + "\nnull",
                        CHAT_ID_ADMIN,
                        Optional.of(MESSAGE_ID_ADMIN)
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ERROR_MONITORING
                                + DEVICE_ID
                                + EventService.PAUSED,
                        CHAT_ID_USER,
                        Optional.of(MESSAGE_ID_USER)
                );
    }

    @Test
    void pingStartErrorStartPingRequest() throws Exception {
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        ZonedDateTime timeSecondEvent = INIT_TIME.plusMinutes(1);
        ZonedDateTime timeError = timeSecondEvent.plusMinutes(1);
        ZonedDateTime timeFourthEvent = timeError.plusMinutes(1);
        ZonedDateTime timeSending = timeError.plusMinutes(1);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(INIT_TIME),
                                null
                        ),
                        new Event(
                                2L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeSecondEvent),
                                null
                        ),
                        new Event(
                                3L,
                                EventType.ERROR,
                                FormattersKt.dateTimeFormatter.format(timeError),
                                null
                        ),
                        new Event(
                                4L,
                                EventType.START,
                                FormattersKt.dateTimeFormatter.format(timeFourthEvent),
                                null
                        ),
                        new Event(
                                5L,
                                EventType.PING,
                                FormattersKt.dateTimeFormatter.format(timeSending),
                                null
                        )
                ),
                DEVICE_ID
        );

        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID)).thenReturn(Optional.of(new DeviceInfo(true, false)));
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));

        eventService.processEvents(eventRequest);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ERROR_MONITORING
                                + DEVICE_ID
                                + EventService.PAUSED
                                + "\nnull",
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );

        inOrder.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ERROR_MONITORING
                                + DEVICE_ID
                                + EventService.PAUSED,
                        CHAT_ID_USER,
                        Optional.empty()
                );

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void retryAfterRemoval() throws Exception {
        // large period so the countdown timer never fires during this test
        EventService.setPeriodMilliseconds(60_000L);
        final EventService eventService = new EventService(telegramNotifierMock, deviceRepositoryMock, clockMock);

        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        FormattersKt.dateTimeFormatter.format(INIT_TIME),
                        null
                )),
                DEVICE_ID
        );

        CountDownLatch threadAHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseThreadA = new CountDownLatch(1);
        AtomicInteger getDeviceInfoCalls = new AtomicInteger();

        // call 1 (thread A, under the processor's lock): park until released, then "device not found";
        // call 2 (thread B, after retry on a fresh processor): device exists
        Mockito.when(deviceRepositoryMock.getDeviceInfo(DEVICE_ID)).thenAnswer(_ -> {
            if (1 == getDeviceInfoCalls.incrementAndGet()) {
                threadAHoldsLock.countDown();
                if (!releaseThreadA.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("thread A was never released");
                }
                return Optional.empty();
            }
            return Optional.of(new DeviceInfo(true, false));
        });
        Mockito.when(deviceRepositoryMock.getDeviceChats(DEVICE_ID))
                .thenReturn(List.of(
                        new Chat(CHAT_ID_ADMIN, true),
                        new Chat(CHAT_ID_USER, false)
                ));
        Mockito.when(clockMock.instant()).thenReturn(INIT_TIME.toInstant());
        Mockito.when(clockMock.getZone()).thenReturn(INIT_TIME.getZone());

        AtomicReference<Throwable> threadAException = new AtomicReference<>();
        Thread threadA = new Thread(() -> {
            try {
                eventService.processEvents(eventRequest);
            } catch (Throwable e) {
                threadAException.set(e);
            }
        }, "test-thread-a");

        AtomicReference<Throwable> threadBException = new AtomicReference<>();
        Thread threadB = new Thread(() -> {
            try {
                eventService.processEvents(eventRequest);
            } catch (Throwable e) {
                threadBException.set(e);
            }
        }, "test-thread-b");

        threadA.start();
        Assertions.assertTrue(threadAHoldsLock.await(5, TimeUnit.SECONDS));

        threadB.start();
        // B has taken the same processor from the map and is now parked on its lock
        awaitState(threadB, Thread.State.WAITING);

        releaseThreadA.countDown();

        threadA.join(5_000);
        threadB.join(5_000);
        Assertions.assertFalse(threadA.isAlive());
        Assertions.assertFalse(threadB.isAlive());

        Assertions.assertInstanceOf(DeviceNotFoundException.class, threadAException.get());
        Assertions.assertNull(threadBException.get());

        Mockito.verify(telegramNotifierMock)
                .send(
                        DEVICE_ID
                                + EventService.ADDED,
                        CHAT_ID_ADMIN,
                        Optional.empty()
                );
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    private void awaitState(Thread thread, Thread.State state) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != state) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException(
                        "thread " + thread.getName() + " did not reach " + state + ", is in " + thread.getState());
            }
            Thread.sleep(1);
        }
    }

    private void sleepUntilOffline() throws InterruptedException {
        Thread.sleep((long) (EventService.getPeriodMilliseconds()
                * DeviceProcessor.COEFFICIENT_WAITING
                * COEFFICIENT_SLEEP
        ));
    }
}
