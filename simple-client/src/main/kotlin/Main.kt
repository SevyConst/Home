package org.example

import io.github.oshai.kotlinlogging.KotlinLogging
import model.Event
import model.EventRequest
import model.EventType
import org.example.config.Config
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private val logger = KotlinLogging.logger {}
private val scheduler = Executors.newSingleThreadScheduledExecutor()

fun main() {
    val config = Config.readEnv()
    logger.info {"Configuration: $config" }

    val httpSender = HttpSender(config.serverUri, config.connectTimeout, config.requestTimeout)

    sendStartMessage(config.deviceId, httpSender)
    scheduler.scheduleWithFixedDelay(
        SenderRunnable(1, config.deviceId, httpSender),
        config.interval.inWholeSeconds,
        config.interval.inWholeSeconds,
        TimeUnit.SECONDS
    )

}

fun sendStartMessage(deviceId: String, httpSender: HttpSender) {
    val event = Event(0, EventType.START, LocalDateTime.now().format(dateTimeFormatter))
    httpSender.send(EventRequest(listOf(event), deviceId))
}