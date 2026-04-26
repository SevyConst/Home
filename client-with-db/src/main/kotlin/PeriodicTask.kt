package org.example

import model.Event
import model.EventRequest
import model.EventType
import org.example.db.Db
import java.time.LocalDateTime
import java.time.YearMonth

class PeriodicTask(
    private var messageId: Long,
    private val deviceId: String,
    private val httpSender: HttpSender,
    private val db: Db): Runnable {

    override fun run() {
        val now = LocalDateTime.now()

        val event = Event(
            messageId++,
            EventType.PING,
            now.format(dateTimeFormatter)
        )

        db.writeEvent(event, YearMonth.of(now.year, now.monthValue))

        httpSender.send(EventRequest(listOf(event), deviceId))

    }
}
