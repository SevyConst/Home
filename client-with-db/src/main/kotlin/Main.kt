package org.example

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive
import model.Event
import model.EventRequest
import model.EventType
import org.example.config.Config
import org.example.config.ConfigDb
import org.example.db.EventDb
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}
private val scheduler = Executors.newSingleThreadScheduledExecutor()
val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
const val ERROR_STACKTRACE = "error stacktrace"
const val ERROR_MESSAGE = "error message"

fun main() {
    val config = Config.readEnv()
    logger.info {"Configuration: $config" }

    val httpSender = HttpSender(config.serverUri, config.connectTimeout, config.requestTimeout)

    val configDb = ConfigDb.readEnv()
    logger.info {"Configuration database: $configDb"}

    val eventDb = try {
        EventDb(
            pathWithoutFileName = configDb.pathWithoutFileName,
            maxUnreceivedEvents = configDb.maxUnreceivedEvents,
            numberOfFiles = configDb.numberOfFiles,
            currentYearMonth = YearMonth.now()
        )
    } catch (exception: Exception) {
        val message = "Can't init eventDb"
        logger.error(exception) { message }
        sendError(exception = exception, httpSender = httpSender, deviceId = config.deviceId, message = message)
        exitProcess(1)
    }

    val messageId = try {
        eventDb.getLastId() + 1
    } catch (exception: Exception) {
        val message = "Can't get last id from db"
        logger.error(exception) { message }
        sendError(exception = exception, httpSender = httpSender, deviceId = config.deviceId, message = message)
        exitProcess(1)
    }

    processStartMessage(eventDb, messageId, config.deviceId, httpSender)
    scheduler.scheduleWithFixedDelay(
        PeriodicTask(messageId + 1, config.deviceId, httpSender, eventDb),
        config.interval.inWholeSeconds,
        config.interval.inWholeSeconds,
        TimeUnit.SECONDS
    )
}

fun processStartMessage(
    eventDb: EventDb,
    messageId: Long,
    deviceId: String,
    httpSender: HttpSender
) {
    val now = LocalDateTime.now()
    val event = Event(
        id = messageId,
        eventType = EventType.START,
        time = now.format(dateTimeFormatter),
        additionalInfo = null
    )
    processEvent(
        event = event,
        yearMonth = YearMonth.of(now.year, now.monthValue),
        eventDb = eventDb,
        deviceId = deviceId,
        httpSender = httpSender
    )
}

fun sendError(exception: Exception, httpSender: HttpSender, deviceId: String, message: String) {
    val now = LocalDateTime.now()
    val event = Event(
        id = -1,
        eventType = EventType.ERROR,
        time = now.format(dateTimeFormatter),
        additionalInfo = mapOf(
            ERROR_MESSAGE to JsonPrimitive(message),
            ERROR_STACKTRACE to JsonPrimitive(exception.stackTraceToString())
        )
    )
    httpSender.send(EventRequest(
        listOf(event),
        deviceId,
    ))
}
