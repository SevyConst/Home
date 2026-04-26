package org.example

import io.github.oshai.kotlinlogging.KotlinLogging
import model.Event
import model.EventRequest
import model.EventType
import org.example.config.Config
import org.example.config.ConfigDb
import org.example.db.Db
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}
private val scheduler = Executors.newSingleThreadScheduledExecutor()
val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun main() {
    val config = Config.readEnv()
    logger.info {"Configuration: $config" }

    val configDb = ConfigDb.readEnv()
    logger.info {"Configuration database: $configDb"}

    val db = Db(
        pathWithoutFileName = configDb.pathWithoutFileName,
        maxUnreceivedEvents = configDb.maxUnreceivedEvents,
        numberOfFiles = configDb.numberOfFiles,
        currentYearMonth = YearMonth.now()
    )

    val httpSender = HttpSender(config.serverUri, config.connectTimeout, config.requestTimeout)

    val messageId = db.getLastId() + 1
    processStartMessage(db, messageId, config.deviceId, httpSender)
    scheduler.scheduleWithFixedDelay(
        PeriodicTask(messageId + 1, config.deviceId, httpSender, db),
        config.interval.inWholeSeconds,
        config.interval.inWholeSeconds,
        TimeUnit.SECONDS
    )
}

fun processStartMessage(db:Db, messageId: Long, deviceId: String, httpSender: HttpSender) {
    val now = LocalDateTime.now()
    val event = Event(messageId, EventType.START, now.format(dateTimeFormatter))
    db.writeEvent(event, YearMonth.of(now.year, now.monthValue))

    httpSender.send(EventRequest(listOf(event), deviceId))
}