package org.example.server.service;

import model.Event;
import model.EventRequest;
import model.EventType;
import org.example.FormattersKt;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

class EventServiceTest {

    private static final String DEVICE_ID = "test_device_id";
    private static final long PERIOD_MILLISECONDS = 50L;
    private static final double COEFFICIENT_SLEEP = 1.1;
    private final TelegramNotifier telegramNotifierMock = Mockito.mock(TelegramNotifier.class);
    private final EventService eventService = new EventService(telegramNotifierMock);

    @Test
    void pingRequestPingRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED);
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void doublePingOneRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);

        LocalDateTime time2 = time1.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.PING,
                                clientFormattedTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                clientFormattedTimeString2,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED);
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void pingRequestPausePingRequest() throws Exception {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + DeviceProcessor.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.WITHOUT_INTERNET + serverFormattedTimeString1
                        + EventService.UNTIL +  serverFormattedTimeString2 + "\n");
        inOrder.verifyNoMoreInteractions();

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    @Test
    void pingRequestStartRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.NO_POWER + serverFormattedTimeString1
                        + EventService.UNTIL +  serverFormattedTimeString2);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestPauseStartRequest() throws Exception {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + DeviceProcessor.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.NO_POWER + serverFormattedTimeString1
                        + EventService.UNTIL +  serverFormattedTimeString2);
        inOrder.verifyNoMoreInteractions();

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    @Test
    void startRequestStartRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED + EventService.TURN_ON + serverFormattedTimeString1);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.NO_POWER + serverFormattedTimeString1
                        + EventService.UNTIL +  serverFormattedTimeString2);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void doubleStartOneRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);

        LocalDateTime time2 = time1.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                clientFormattedTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.START,
                                clientFormattedTimeString2,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON + serverFormattedTimeString1
                        + EventService.NO_POWER + serverFormattedTimeString1
                        + EventService.UNTIL +  serverFormattedTimeString2);
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void startRequestPauseStartRequest() throws Exception {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON +  serverFormattedTimeString1);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + DeviceProcessor.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.NO_POWER + serverFormattedTimeString1
                        + EventService.UNTIL +  serverFormattedTimeString2);
        inOrder.verifyNoMoreInteractions();

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    @Test
    void startRequestPingRequestStartRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        LocalDateTime time3 = LocalDateTime.now();
        String clientFormattedTimeString3 = FormattersKt.dateTimeFormatter.format(time3);
        String serverFormattedTimeString3 = EventService.formatterWithoutSeconds.format(time3);
        EventRequest eventRequest3 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        clientFormattedTimeString3,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest3);


        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock, Mockito.times(1))
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON + serverFormattedTimeString1);
        inOrder.verify(telegramNotifierMock, Mockito.times(1))
                .send(DEVICE_ID + ":" + EventService.NO_POWER + serverFormattedTimeString2
                        + EventService.UNTIL + serverFormattedTimeString3);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void startPingStartOneRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);

        LocalDateTime time2 = time1.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);

        LocalDateTime time3 = time2.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientFormattedTimeString3 = FormattersKt.dateTimeFormatter.format(time3);
        String serverFormattedTimeString3 = EventService.formatterWithoutSeconds.format(time3);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                clientFormattedTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                clientFormattedTimeString2,
                                null
                        ),
                        new Event(
                                3L,
                                EventType.START,
                                clientFormattedTimeString3,
                                null
                        )
                ),
                DEVICE_ID
        );

        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":"
                        + EventService.ADDED
                        + EventService.TURN_ON + serverFormattedTimeString1
                        + EventService.NO_POWER + serverFormattedTimeString2
                        + EventService.UNTIL + serverFormattedTimeString3);
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    /**
     * Two requests
     */
    @Test
    void startRequestPingStartRequest() throws Exception {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        clientFormattedTimeString1,
                        null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);

        Thread.sleep(EventService.getPeriodMilliseconds());

        LocalDateTime time3 = LocalDateTime.now();
        String clientFormattedTimeString3 = FormattersKt.dateTimeFormatter.format(time3);
        String serverFormattedTimeString3 = EventService.formatterWithoutSeconds.format(time3);

        EventRequest eventRequest2 = new EventRequest(
                List.of(
                        new Event(
                                2L,
                                EventType.PING,
                                clientFormattedTimeString2,
                                null
                        ),
                        new Event(
                                3L,
                                EventType.START,
                                clientFormattedTimeString3,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                        .send(DEVICE_ID + ":" + EventService.ADDED
                                + EventService.TURN_ON + serverFormattedTimeString1);
        inOrder.verify(telegramNotifierMock)
                        .send(DEVICE_ID + DeviceProcessor.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.WITHOUT_INTERNET +  serverFormattedTimeString1
                        + EventService.UNTIL + serverFormattedTimeString3 + "\n"
                        + EventService.NO_POWER + serverFormattedTimeString2
                        + EventService.UNTIL + serverFormattedTimeString3);
        inOrder.verifyNoMoreInteractions();

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    /**
     * Two requests
     */
    @Test
    void startPingRequestStartRequest() throws Exception {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);

        EventRequest eventRequest1 = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                clientFormattedTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                clientFormattedTimeString2,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time3 = LocalDateTime.now();
        String clientFormattedTimeString3 = FormattersKt.dateTimeFormatter.format(time3);
        String serverFormattedTimeString3 = EventService.formatterWithoutSeconds.format(time3);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                                3L,
                                EventType.START,
                                clientFormattedTimeString3,
                                null
                        )
                ),
                DEVICE_ID
        );

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON + serverFormattedTimeString1);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + DeviceProcessor.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.NO_POWER + serverFormattedTimeString2
                        + EventService.UNTIL + serverFormattedTimeString3);
        inOrder.verifyNoMoreInteractions();

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    /**
     * Two requests
     */
    @Test
    void startRequestPingPingStartPingRequest() throws Exception {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                                1L,
                                EventType.START,
                                clientFormattedTimeString1,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);

        Thread.sleep(EventService.getPeriodMilliseconds());

        LocalDateTime time3 = LocalDateTime.now();
        String clientFormattedTimeString3 = FormattersKt.dateTimeFormatter.format(time3);
        String serverFormattedTimeString3 = EventService.formatterWithoutSeconds.format(time3);

        LocalDateTime time4 = LocalDateTime.now();
        String clientFormattedTimeString4 = FormattersKt.dateTimeFormatter.format(time4);
        String serverFormattedTimeString4 = EventService.formatterWithoutSeconds.format(time4);

        LocalDateTime time5 = LocalDateTime.now();
        String clientFormattedTimeString5 = FormattersKt.dateTimeFormatter.format(time5);
        String serverFormattedTimeString5 = EventService.formatterWithoutSeconds.format(time5);

        EventRequest eventRequest2 = new EventRequest(
                List.of(
                        new Event(
                                2L,
                                EventType.PING,
                                clientFormattedTimeString2,
                                null
                        ),
                        new Event(
                                3L,
                                EventType.PING,
                                clientFormattedTimeString3,
                                null
                        ),
                        new Event(
                                4L,
                                EventType.START,
                                clientFormattedTimeString4,
                                null
                        ),
                        new Event(5L,
                                EventType.PING,
                                clientFormattedTimeString5,
                                null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON + serverFormattedTimeString1);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + DeviceProcessor.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.WITHOUT_INTERNET +  serverFormattedTimeString1
                        + EventService.UNTIL + serverFormattedTimeString5 + "\n"
                        + EventService.NO_POWER + serverFormattedTimeString3
                        + EventService.UNTIL + serverFormattedTimeString4);
        inOrder.verifyNoMoreInteractions();

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    @Test
    void testOnlyError() {
        LocalDateTime time = LocalDateTime.now();
        String clientFormattedTimeString = FormattersKt.dateTimeFormatter.format(time);
        String serverFormattedTimeString = EventService.formatterWithoutSeconds.format(time);
        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.ERROR,
                        clientFormattedTimeString,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.ERROR + serverFormattedTimeString
                        + " :\n\"null\"");
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void pingRequestErrorRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.ERROR,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":"
                        + EventService.ERROR + serverFormattedTimeString2
                        + " :\n\"null\"");
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestPauseErrorRequest() throws InterruptedException {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.ERROR,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + DeviceProcessor.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.WITHOUT_INTERNET + serverFormattedTimeString1
                        + EventService.UNTIL + serverFormattedTimeString2 + "\n"
                        + EventService.ERROR + serverFormattedTimeString2
                        + " :\n\"null\"");
        inOrder.verifyNoMoreInteractions();

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    @Test
    void startRequestErrorRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.ERROR,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON + serverFormattedTimeString1);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":"
                        + EventService.ERROR + serverFormattedTimeString2
                        + " :\n\"null\"");
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void startRequestPauseErrorRequest() throws InterruptedException {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.ERROR,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON +  serverFormattedTimeString1);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + DeviceProcessor.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.WITHOUT_INTERNET + serverFormattedTimeString1
                        + EventService.UNTIL + serverFormattedTimeString2 + "\n"
                        + EventService.ERROR + serverFormattedTimeString2
                        + " :\n\"null\"");
        inOrder.verifyNoMoreInteractions();

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    @Test
    void pingErrorRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);

        LocalDateTime time2 = time1.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.PING,
                                clientFormattedTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.ERROR,
                                clientFormattedTimeString2,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.ERROR + serverFormattedTimeString2
                        + " :\n\"null\"");
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void startErrorRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);

        LocalDateTime time2 = time1.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverFormattedTimeString2 = EventService.formatterWithoutSeconds.format(time2);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                clientFormattedTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.ERROR,
                                clientFormattedTimeString2,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON + serverFormattedTimeString1
                        + EventService.ERROR + serverFormattedTimeString2
                        + " :\n\"null\"");
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    /**
     * Ignore events after error
     */
    @Test
    void errorPingRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);

        LocalDateTime time2 = time1.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.ERROR,
                                clientFormattedTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                clientFormattedTimeString2,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.ERROR + serverFormattedTimeString1
                        + " :\n\"null\"");
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    /**
     * Ignore events after error
     */
    @Test
    void errorStartRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);

        LocalDateTime time2 = time1.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.ERROR,
                                clientFormattedTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.START,
                                clientFormattedTimeString2,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.ERROR + serverFormattedTimeString1
                        + " :\n\"null\"");
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    /**
     * Ignore events after error
     */
    @Test
    void errorRequestPauseErrorRequest() throws InterruptedException {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.ERROR,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.ERROR,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.ERROR + serverFormattedTimeString1
                        + " :\n\"null\"");
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    /**
     * Ignore events after error
     */
    @Test
    void errorRequestPauseStartRequest() throws InterruptedException {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.ERROR,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.ERROR + serverFormattedTimeString1
                        + " :\n\"null\"");
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    /**
     * Ignore events after error
     */
    @Test
    void errorRequestPausePingRequest() throws InterruptedException {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.ERROR,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.ERROR + serverFormattedTimeString1
                        + " :\n\"null\"");
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    /**
     * Ignore events after error
     */
    @Test
    void errorRequestPingRequestPausePingRequest() throws InterruptedException {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.ERROR,
                        clientFormattedTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientFormattedTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        sleepUntilOffline();

        LocalDateTime time3 = LocalDateTime.now();
        String clientFormattedTimeString3 = FormattersKt.dateTimeFormatter.format(time3);
        EventRequest eventRequest3 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientFormattedTimeString3,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest3);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.ERROR + serverFormattedTimeString1
                        + " :\n\"null\"");
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    /**
     * Ignore events after error
     */
    @Test
    void errorStartRequestPausePingRequest() throws InterruptedException {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientFormattedTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverFormattedTimeString1 = EventService.formatterWithoutSeconds.format(time1);

        LocalDateTime time2 = time1.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientFormattedTimeString2 = FormattersKt.dateTimeFormatter.format(time2);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.ERROR,
                                clientFormattedTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.START,
                                clientFormattedTimeString2,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest);

        sleepUntilOffline();

        LocalDateTime time3 = LocalDateTime.now();
        String clientFormattedTimeString3 = FormattersKt.dateTimeFormatter.format(time3);
        EventRequest eventRequest3 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientFormattedTimeString3,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest3);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.ERROR + serverFormattedTimeString1
                        + " :\n\"null\"");
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    private void sleepUntilOffline() throws InterruptedException {
        Thread.sleep((long) (EventService.getPeriodMilliseconds()
                * DeviceProcessor.COEFFICIENT_WAITING
                * COEFFICIENT_SLEEP
        ));
    }
}
