package org.example

import io.github.oshai.kotlinlogging.KotlinLogging
import model.Event
import model.EventRequest
import model.EventType
import org.example.config.Config
import java.time.OffsetDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


private val logger = KotlinLogging.logger {}
private val scheduler = Executors.newSingleThreadScheduledExecutor()

fun main() {
    val config = Config.readEnv()
    logger.info {"Configuration: $config" }

    val httpSender = HttpSender(config.serverUri, config.connectTimeout, config.requestTimeout)

    processStartMessage(config.deviceId, httpSender)
    scheduler.scheduleWithFixedDelay(
        PeriodicTask(1, config.deviceId, httpSender),
        PING_PERIOD_MILLISECONDS,
        PING_PERIOD_MILLISECONDS,
        TimeUnit.MILLISECONDS
    )

}

fun processStartMessage(deviceId: String, httpSender: HttpSender) {
    val event = Event(
        id = 0,
        eventType = EventType.START,
        time = OffsetDateTime.now().format(dateTimeFormatter),
        additionalInfo = null)
    httpSender.send(EventRequest(listOf(event), deviceId))
}
