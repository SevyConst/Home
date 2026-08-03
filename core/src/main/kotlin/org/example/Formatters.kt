package org.example

import java.time.format.DateTimeFormatter

// Format of Event.time on the wire and in the client database: ISO-8601 with an offset,
// e.g. 2026-08-02T21:48:00+03:00. The offset makes every event a self-contained instant,
// so the displayed time does not depend on how the client OS timezone is configured.
@JvmField
val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
