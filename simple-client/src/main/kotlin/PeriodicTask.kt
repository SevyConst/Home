package org.example

import model.Event
import model.EventRequest
import model.EventType
import java.time.LocalDateTime

class PeriodicTask(
    private var messageId: Long,
    private val deviceId: String,
    private val httpSender: HttpSender): Runnable {

    override fun run() {
        val event = Event(messageId++,
            EventType.PING,
            LocalDateTime.now().format(dateTimeFormatter))

        httpSender.send(EventRequest(listOf(event), deviceId))

    }
}
