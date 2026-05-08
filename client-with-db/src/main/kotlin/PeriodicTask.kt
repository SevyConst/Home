package org.example

import model.Event
import model.EventRequest
import model.EventType
import org.example.db.EventDb
import java.time.LocalDateTime
import java.time.YearMonth

class PeriodicTask(
    private var messageId: Long,
    private val deviceId: String,
    private val httpSender: HttpSender,
    private val eventDb: EventDb
): Runnable {

    override fun run() {
        val now = LocalDateTime.now()
        val event = Event(messageId++, EventType.PING, now.format(dateTimeFormatter))
        processEvent(event, YearMonth.of(now.year, now.monthValue), eventDb, deviceId, httpSender)
    }
}

fun processEvent(
    event: Event,
    yearMonth: YearMonth,
    eventDb: EventDb,
    deviceId: String,
    httpSender: HttpSender
) {
    eventDb.writeEvent(event, yearMonth)

    val collected = mutableListOf<Event>()
    val isPreviousConnectionUsed = eventDb.readUnreceivedTail(collected)
    if (collected.isEmpty()) {
        return
    }

    collected.reverse()
    val sent = httpSender.send(EventRequest(collected, deviceId))
    if (sent) {
        eventDb.markReceived(collected.map { it.id }, isPreviousConnectionUsed)
    }
}
