package org.example

import io.github.oshai.kotlinlogging.KotlinLogging
import model.Event
import model.EventRequest
import model.EventType
import org.example.db.EventDb
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

class PeriodicTask(
    private var messageId: Long,
    private val deviceId: String,
    private val httpSender: HttpSender,
    private val eventDb: EventDb
): Runnable {

    override fun run() {
        val now = LocalDateTime.now()
        val event = Event(messageId++, EventType.PING, now.format(dateTimeFormatter), null)
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
    try {
        eventDb.writeEvent(event, yearMonth)
    } catch (exception: Exception) {
        val message = "Can't save event to db"
        logger.error(exception) { message }
        sendError(exception = exception, httpSender = httpSender, deviceId = deviceId, message = message)
        exitProcess(1)
    }

    val collected = mutableListOf<Event>()

    val isPreviousConnectionUsed = try {
        eventDb.readUnreceivedTail(collected)
    } catch (exception: Exception) {
        val message = "Can't read events from db"
        logger.error(exception) { message }
        sendError(exception = exception, httpSender = httpSender, deviceId = deviceId, message = message)
        exitProcess(1)
    }

    if (collected.isEmpty()) {
        return
    }

    collected.reverse()
    val sent = httpSender.send(EventRequest(collected, deviceId))
    if (sent) {
        try {
            eventDb.markReceived(collected.map { it.id }, isPreviousConnectionUsed)
        } catch (exception: Exception) {
            val message = "Can't mark events db"
            logger.error(exception) { message }
            sendError(exception = exception, httpSender = httpSender, deviceId = deviceId, message = message)
            exitProcess(1)
        }
    }
}
