package org.example

import java.time.ZoneId
import java.time.format.DateTimeFormatter

@JvmField
val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss yyyy-MM-dd").withZone(ZoneId.systemDefault())
