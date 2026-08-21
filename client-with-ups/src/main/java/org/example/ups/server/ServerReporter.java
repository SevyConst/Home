package org.example.ups.server;

import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonElementKt;
import model.Event;
import model.EventRequest;
import model.EventType;
import org.example.FormattersKt;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class ServerReporter {

    private static final String ERROR_DETAIL_KEY = "detail";

    private final EventSender sender;
    private final String deviceId;
    private final Clock clock;
    /**
     * The id of the next event. A plain field rather than an atomic one: every event this class
     * sends is sent from the single {@code server-ping} thread, so there is nothing to contend
     * with. Anything sending from a second thread would have to revisit this.
     */
    private long nextId;

    public ServerReporter(EventSender sender, String deviceId, Clock clock) {
        this.sender = sender;
        this.deviceId = deviceId;
        this.clock = clock;
    }

    public void sendStart() {
        try {
            send(EventType.START, OffsetDateTime.now(clock), null);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean sendPing() {
        try {
            return send(EventType.PING, OffsetDateTime.now(clock), null);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO
    public boolean sendFatalError(String detail) throws InterruptedException {
        return send(
                EventType.ERROR,
                OffsetDateTime.now(clock),
                Map.of(ERROR_DETAIL_KEY, JsonElementKt.JsonPrimitive(detail))
        );
    }

    private boolean send(
            EventType type,
            OffsetDateTime time,
            Map<String, JsonElement> info
    ) throws InterruptedException {

        Event event = new Event(
                nextId++,
                type,
                FormattersKt.dateTimeFormatter.format(time),
                info
        );

        return sender.send(new EventRequest(List.of(event), deviceId));
    }
}
