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
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        clientTimeString2,
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
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);

        LocalDateTime time2 = time1.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.PING,
                                clientTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                clientTimeString2,
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
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        clientTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + CountdownTimer.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.WITHOUT_INTERNET + serverTimeString1
                        + EventService.UNTIL +  serverTimeString2 + "\n");
        inOrder.verifyNoMoreInteractions();

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    @Test
    void pingRequestStartRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        clientTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.NO_POWER + serverTimeString1
                        + EventService.UNTIL +  serverTimeString2);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void pingRequestPauseStartRequest() throws Exception {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.PING,
                        clientTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        clientTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + CountdownTimer.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.NO_POWER + serverTimeString1
                        + EventService.UNTIL +  serverTimeString2);
        inOrder.verifyNoMoreInteractions();

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    @Test
    void startRequestStartRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        clientTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        clientTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED + EventService.TURN_ON + serverTimeString1);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.NO_POWER + serverTimeString1
                        + EventService.UNTIL +  serverTimeString2);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void doubleStartOneRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverTimeString1 = EventService.formatterWithoutSeconds.format(time1);

        LocalDateTime time2 = time1.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverTimeString2 = EventService.formatterWithoutSeconds.format(time2);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                clientTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.START,
                                clientTimeString2,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON + serverTimeString1
                        + EventService.NO_POWER + serverTimeString1
                        + EventService.UNTIL +  serverTimeString2);
        Mockito.verifyNoMoreInteractions(telegramNotifierMock);
    }

    @Test
    void startRequestPauseStartRequest() throws Exception {
        long sleepMillisecondsDefault = EventService.getPeriodMilliseconds();
        EventService.setPeriodMilliseconds(PERIOD_MILLISECONDS);

        LocalDateTime time1 = LocalDateTime.now();
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        clientTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        clientTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON +  serverTimeString1);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + CountdownTimer.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.NO_POWER + serverTimeString1
                        + EventService.UNTIL +  serverTimeString2);
        inOrder.verifyNoMoreInteractions();

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    @Test
    void startRequestPingRequestStartRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        clientTimeString1,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverTimeString2 = EventService.formatterWithoutSeconds.format(time2);
        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.PING,
                        clientTimeString2,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        LocalDateTime time3 = LocalDateTime.now();
        String clientTimeString3 = FormattersKt.dateTimeFormatter.format(time3);
        String serverTimeString3 = EventService.formatterWithoutSeconds.format(time3);
        EventRequest eventRequest3 = new EventRequest(
                List.of(new Event(
                        2L,
                        EventType.START,
                        clientTimeString3,
                        null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest3);


        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock, Mockito.times(1))
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON + serverTimeString1);
        inOrder.verify(telegramNotifierMock, Mockito.times(1))
                .send(DEVICE_ID + ":" + EventService.NO_POWER + serverTimeString2
                        + EventService.UNTIL + serverTimeString3);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void startPingStartOneRequest() {
        LocalDateTime time1 = LocalDateTime.now();
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverTimeString1 = EventService.formatterWithoutSeconds.format(time1);

        LocalDateTime time2 = time1.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverTimeString2 = EventService.formatterWithoutSeconds.format(time2);

        LocalDateTime time3 = time2.plusSeconds(EventService.getPeriodMilliseconds() / 1000);
        String clientTimeString3 = FormattersKt.dateTimeFormatter.format(time3);
        String serverTimeString3 = EventService.formatterWithoutSeconds.format(time3);

        EventRequest eventRequest = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                clientTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                clientTimeString2,
                                null
                        ),
                        new Event(
                                3L,
                                EventType.START,
                                clientTimeString3,
                                null
                        )
                ),
                DEVICE_ID
        );

        eventService.processEvents(eventRequest);

        Mockito.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":"
                        + EventService.ADDED
                        + EventService.TURN_ON + serverTimeString1
                        + EventService.NO_POWER + serverTimeString2
                        + EventService.UNTIL + serverTimeString3);
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
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                        1L,
                        EventType.START,
                        clientTimeString1,
                        null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverTimeString2 = EventService.formatterWithoutSeconds.format(time2);

        Thread.sleep(EventService.getPeriodMilliseconds());

        LocalDateTime time3 = LocalDateTime.now();
        String clientTimeString3 = FormattersKt.dateTimeFormatter.format(time3);
        String serverTimeString3 = EventService.formatterWithoutSeconds.format(time3);

        EventRequest eventRequest2 = new EventRequest(
                List.of(
                        new Event(
                                2L,
                                EventType.PING,
                                clientTimeString2,
                                null
                        ),
                        new Event(
                                3L,
                                EventType.START,
                                clientTimeString3,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                        .send(DEVICE_ID + ":" + EventService.ADDED
                                + EventService.TURN_ON + serverTimeString1);
        inOrder.verify(telegramNotifierMock)
                        .send(DEVICE_ID + CountdownTimer.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.WITHOUT_INTERNET +  serverTimeString1
                        + EventService.UNTIL + serverTimeString3 + "\n"
                        + EventService.NO_POWER + serverTimeString2
                        + EventService.UNTIL + serverTimeString3);
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
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverTimeString1 = EventService.formatterWithoutSeconds.format(time1);

        LocalDateTime time2 = LocalDateTime.now();
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);
        String serverTimeString2 = EventService.formatterWithoutSeconds.format(time2);

        EventRequest eventRequest1 = new EventRequest(
                List.of(
                        new Event(
                                1L,
                                EventType.START,
                                clientTimeString1,
                                null
                        ),
                        new Event(
                                2L,
                                EventType.PING,
                                clientTimeString2,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time3 = LocalDateTime.now();
        String clientTimeString3 = FormattersKt.dateTimeFormatter.format(time3);
        String serverTimeString3 = EventService.formatterWithoutSeconds.format(time3);

        EventRequest eventRequest2 = new EventRequest(
                List.of(new Event(
                                3L,
                                EventType.START,
                                clientTimeString3,
                                null
                        )
                ),
                DEVICE_ID
        );

        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON + serverTimeString1);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + CountdownTimer.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.NO_POWER + serverTimeString2
                        + EventService.UNTIL + serverTimeString3);
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
        String clientTimeString1 = FormattersKt.dateTimeFormatter.format(time1);
        String serverTimeString1 = EventService.formatterWithoutSeconds.format(time1);
        EventRequest eventRequest1 = new EventRequest(
                List.of(new Event(
                                1L,
                                EventType.START,
                                clientTimeString1,
                                null
                        )
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest1);

        sleepUntilOffline();

        LocalDateTime time2 = LocalDateTime.now();
        String clientTimeString2 = FormattersKt.dateTimeFormatter.format(time2);

        Thread.sleep(EventService.getPeriodMilliseconds());

        LocalDateTime time3 = LocalDateTime.now();
        String clientTimeString3 = FormattersKt.dateTimeFormatter.format(time3);
        String serverTimeString3 = EventService.formatterWithoutSeconds.format(time3);

        LocalDateTime time4 = LocalDateTime.now();
        String clientTimeString4 = FormattersKt.dateTimeFormatter.format(time4);
        String serverTimeString4 = EventService.formatterWithoutSeconds.format(time4);

        LocalDateTime time5 = LocalDateTime.now();
        String clientTimeString5 = FormattersKt.dateTimeFormatter.format(time5);
        String serverTimeString5 = EventService.formatterWithoutSeconds.format(time5);

        EventRequest eventRequest2 = new EventRequest(
                List.of(
                        new Event(
                                2L,
                                EventType.PING,
                                clientTimeString2,
                                null
                        ),
                        new Event(
                                3L,
                                EventType.PING,
                                clientTimeString3,
                                null
                        ),
                        new Event(
                                4L,
                                EventType.START,
                                clientTimeString4,
                                null
                        ),
                        new Event(5L,
                                EventType.PING,
                                clientTimeString5,
                                null)
                ),
                DEVICE_ID
        );
        eventService.processEvents(eventRequest2);

        InOrder inOrder = Mockito.inOrder(telegramNotifierMock);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.ADDED
                        + EventService.TURN_ON + serverTimeString1);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + CountdownTimer.OFFLINE);
        inOrder.verify(telegramNotifierMock)
                .send(DEVICE_ID + ":" + EventService.WITHOUT_INTERNET +  serverTimeString1
                        + EventService.UNTIL + serverTimeString5 + "\n"
                        + EventService.NO_POWER + serverTimeString3
                        + EventService.UNTIL + serverTimeString4);
        inOrder.verifyNoMoreInteractions();

        EventService.setPeriodMilliseconds(sleepMillisecondsDefault);
    }

    private void sleepUntilOffline() throws InterruptedException {
        Thread.sleep((long) (EventService.getPeriodMilliseconds()
                * CountdownTimer.COEFFICIENT_WAITING
                * COEFFICIENT_SLEEP
        ));
    }
}
