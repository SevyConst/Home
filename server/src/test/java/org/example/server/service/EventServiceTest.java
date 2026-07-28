package org.example.server.service;

import model.Event;
import model.EventRequest;
import model.EventType;
import org.example.FormattersKt;
import org.example.server.model.dto.Chat;
import org.example.server.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.*;
import java.util.List;
import java.util.Optional;

class EventServiceTest {

    private static final String DEVICE_ID = "test_device_id";
    private static final long CHAT_ID_ADMIN = 10L;
    private static final long CHAT_ID_USER = 11L;
    private static final int MESSAGE_ID_ADMIN = 100500;
    private static final int MESSAGE_ID_USER = 100600;
    private static final long PERIOD_MILLISECONDS = 100L;
    private static final double COEFFICIENT_SLEEP = 1.1;
    private static final ZonedDateTime INIT_TIME =
            Instant.parse("2026-07-06T13:56:00Z").atZone(ZoneId.systemDefault());
    private static final ZonedDateTime ANOTHER_DAY =
            Instant.parse("2026-07-08T13:56:00Z").atZone(ZoneId.systemDefault());

    private final TelegramNotifier telegramNotifierMock = Mockito.mock(TelegramNotifier.class);
    private final DeviceRepository deviceRepositoryMock = Mockito.mock(DeviceRepository.class);
    private final Clock clockMock = Mockito.mock(Clock.class);

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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID)).thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID)).thenReturn(Optional.of(false));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID)).thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID)).thenReturn(Optional.of(false));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID)).thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(false));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(false));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(false));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(false));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(false));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(false));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(false));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(false));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(false));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID)).thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID))
                .thenReturn(Optional.of(true));
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

        Mockito.when(deviceRepositoryMock.getHasClock(DEVICE_ID)).thenReturn(Optional.of(true));
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

    private void sleepUntilOffline() throws InterruptedException {
        Thread.sleep((long) (EventService.getPeriodMilliseconds()
                * DeviceProcessor.COEFFICIENT_WAITING
                * COEFFICIENT_SLEEP
        ));
    }
}
