package org.example.db

import model.Event
import model.EventType
import java.io.File
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.time.YearMonth

private const val CREATE_TABLE = """
    CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY,
    event_type TEXT NOT NULL,
    is_received INTEGER NOT NULL,
    time TEXT NOT NULL
    )
    """
private const val INSERT_EVENT =
    "INSERT INTO events (id, event_type, is_received, time) VALUES (?, ?, ?, ?)"
private const val READ_MAX_ID = "SELECT max(id) as max_id from events"
private const val READ_TAIL_DESC =
    "SELECT id, event_type, time, is_received FROM events ORDER BY id DESC LIMIT ?"
private const val MARK_RECEIVED = "UPDATE events SET is_received = 1 WHERE id IN (%s)"

private const val SQLITE = "jdbc:sqlite:"
private const val FILE_DB_EXTENSION = ".db"

class EventDb(
    private val pathWithoutFileName: String,
    private val maxUnreceivedEvents: Int,
    private val numberOfFiles: Int,
    private var currentYearMonth: YearMonth
) {
    private var currentMonthConnection: Connection
    private var previousMonthConnection: Connection? = null

    init {
        currentMonthConnection = DriverManager.getConnection(SQLITE + createPath(currentYearMonth))
        createTableIfNotExists()
        openConnectionPreviousMonthFile()
    }

    private fun createPath(yearMonth: YearMonth): String {
        val result = StringBuilder("$pathWithoutFileName${yearMonth.year}_")
        val monthValue = yearMonth.monthValue
        if (monthValue < 10) {
            result.append(0)
        }
        result.append(monthValue)
        result.append(FILE_DB_EXTENSION)

        return result.toString()
    }


    private fun createTableIfNotExists() {
        currentMonthConnection.createStatement().use { statement ->
            statement.execute(CREATE_TABLE)
        }
    }

    private fun openConnectionPreviousMonthFile() {
        val previousMonthYearMonth = currentYearMonth.minusMonths(1)

        val previousMonthPathString = createPath(yearMonth = previousMonthYearMonth)
        val previousMonthFile = File(previousMonthPathString)
        if (previousMonthFile.exists()) {
            previousMonthConnection = DriverManager.getConnection(SQLITE + createPath(previousMonthYearMonth))
        }
    }

    fun getLastId(): Long {
        val lastId = getLastIdFromConnection(currentMonthConnection)

        // start from 1
        // TODO check
        if (lastId != 0L) {
            return lastId
        }

        val previousMonthConnectionVal = previousMonthConnection ?: return 0L
        return getLastIdFromConnection(previousMonthConnectionVal)
    }

    private fun getLastIdFromConnection(connection: Connection): Long {
        connection.createStatement().use { statement ->
            val resultSet = statement.executeQuery(READ_MAX_ID)
            if (!resultSet.next()) {
                throw IllegalStateException("can't get max id from db")
            }

            return resultSet.getLong("max_id")
        }
    }

    fun writeEvent(event: Event, yearMonth: YearMonth) {
        if (currentYearMonth != yearMonth) {
            if (currentYearMonth.plusMonths(1) == yearMonth) {
                processNewMonth(yearMonth)
            } else {
                throw IllegalArgumentException("wrong date")
            }
        }

        currentMonthConnection.prepareStatement(INSERT_EVENT).use { preparedStatement ->
            preparedStatement.setLong(1, event.id)
            preparedStatement.setString(2, event.eventType.toString())
            preparedStatement.setInt(3, 0)
            preparedStatement.setString(4, event.time)
            preparedStatement.executeUpdate()
        }
    }

    private fun processNewMonth(yearMonth: YearMonth) {
        previousMonthConnection?.close()

        deleteTheOldestFile(yearMonth)

        previousMonthConnection = currentMonthConnection
        currentMonthConnection = DriverManager.getConnection(SQLITE + createPath(yearMonth))

        currentYearMonth = yearMonth

        createTableIfNotExists()
    }

    private fun deleteTheOldestFile(yearMonth: YearMonth) {
        val file = File(createPath(yearMonth.minusMonths(numberOfFiles.toLong())))
        if (!file.exists()) {
            return
        }
        if (!file.delete()) {
            throw IOException("Failed to delete file: $file")
        }
    }

    fun readUnreceivedTail(collected: MutableList<Event>): Boolean {

        val stop = readUnreceivedFromConnection(currentMonthConnection, maxUnreceivedEvents, collected)

        var isPreviousConnectionUsed = false
        val previousConnection = previousMonthConnection
        if (!stop && previousConnection != null) {
            readUnreceivedFromConnection(previousConnection, maxUnreceivedEvents - collected.size, collected)
            isPreviousConnectionUsed = true
        }

        return isPreviousConnectionUsed
    }

    // Returns true to stop walking older files: either an already-received row was hit
    // or the limit was reached on this connection.
    private fun readUnreceivedFromConnection(
        connection: Connection,
        limit: Int,
        collector: MutableList<Event>
    ): Boolean {
        connection.prepareStatement(READ_TAIL_DESC).use { statement ->
            statement.setInt(1, limit)
            val resultSet = statement.executeQuery()
            var rowCount = 0
            while (resultSet.next()) {
                rowCount++
                if (resultSet.getInt("is_received") == 1) {
                    return true
                }
                collector.add(
                    Event(
                        id = resultSet.getLong("id"),
                        eventType = EventType.valueOf(resultSet.getString("event_type")),
                        time = resultSet.getString("time"),
                        additionalInfo = null
                    )
                )
            }
            return rowCount >= limit
        }
    }

    fun markReceived(ids: List<Long>, previousConnectionUsed: Boolean) {
        if (ids.isEmpty()) return

        val placeholders = ids.joinToString(",") { "?" }
        val sql = MARK_RECEIVED.format(placeholders)

        markReceivedOnConnection(currentMonthConnection, sql, ids)

        if (previousConnectionUsed) {
            val previousMonthConnectionVal = previousMonthConnection
            if (previousMonthConnectionVal != null) {
                markReceivedOnConnection(previousMonthConnectionVal, sql, ids)
            } else {
                throw IllegalStateException("previous month connection not set")
            }
        }
    }

    private fun markReceivedOnConnection(connection: Connection, sql: String, ids: List<Long>) {
        connection.prepareStatement(sql).use { statement ->
            ids.forEachIndexed { index, id ->
                statement.setLong(index + 1, id)
            }
            statement.executeUpdate()
        }
    }

    fun closeConnections() {
        currentMonthConnection.close()
        previousMonthConnection?.close()
    }
}
