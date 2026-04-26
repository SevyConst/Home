package org.example.db

import io.github.oshai.kotlinlogging.KotlinLogging
import model.Event
import java.io.File
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.YearMonth

private val logger = KotlinLogging.logger {}

private const val CREATE_TABLE = """
    CREATE TABLE IF NOT EXISTS events (
    id BIGINTEGER PRIMARY KEY,
    event_type TEXT NOT NULL,
    is_received INTEGER NOT NULL,
    time TEXT NOT NULL
    )
    """
private const val INSERT_EVENT =
    "INSERT INTO events (id, event_type, is_received, time) VALUES (?, ?, ?, ?)"
private const val READ_MAX_ID = "SELECT max(id) as max_id from events"

private const val SQLITE = "jdbc:sqlite:"
private const val FILE_DB_EXTENSION = ".db"

class Db(
    private val pathWithoutFileName: String,
    private val maxUnreceivedEvents: Int,
    private val numberOfFiles: Int,
    private var currentYearMonth: YearMonth
) {
    private var currentMonthConnection: Connection
    private var previousMonthConnection: Connection? = null

    init {
        try {
            currentMonthConnection = DriverManager.getConnection(SQLITE + createPath(currentYearMonth))
        } catch (e: SQLException) {
            // TODO: send to telegram
            logger.error(e) {}
            throw e
        }
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
        try {
            currentMonthConnection.createStatement().use { statement ->
                statement.execute(CREATE_TABLE)
            }
        } catch (e: SQLException) {
            // TODO: send to telegram
            logger.error(e) {}
            throw e
        }
    }

    private fun openConnectionPreviousMonthFile() {
        val previousMonthYearMonth = currentYearMonth.minusMonths(1)

        val previousMonthPathString = createPath(yearMonth = previousMonthYearMonth)
        val previousMonthFile = File(previousMonthPathString)
        if (previousMonthFile.exists()) {
            try {
                previousMonthConnection = DriverManager.getConnection(SQLITE + createPath(previousMonthYearMonth))
            } catch (e: SQLException) {
                // TODO: send to telegram
                logger.error(e) {}
                throw e
            }
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
        try {
            connection.createStatement().use { statement ->
                val resultSet = statement.executeQuery(READ_MAX_ID)
                if (!resultSet.next()) {
                    val message = "can't get max id from db"
                    // TODO: send to telegram
                    logger.error { message }
                    throw IllegalStateException(message)
                }

                return resultSet.getLong("max_id")
            }
        } catch (e: SQLException) {
            // TODO: send to telegram
            logger.error(e) {}
            throw e
        }
    }

    fun writeEvent(event: Event, yearMonth: YearMonth) {
        if (!currentYearMonth.equals(yearMonth)) {
            if (currentYearMonth.plusMonths(1).equals(yearMonth)) {
                processNewMonth(yearMonth)
            } else {
                val exception = IllegalArgumentException("wring date")
                logger.error { exception }
                throw exception
            }
        }

        try {
            currentMonthConnection.prepareStatement(INSERT_EVENT).use { preparedStatement ->
                preparedStatement.setLong(1, event.id)
                preparedStatement.setString(2, event.eventType.toString())
                preparedStatement.setInt(3, 0)
                preparedStatement.setString(4, event.time)
                preparedStatement.executeUpdate()
            }
        } catch(e: SQLException) {
            // TODO: send to telegram
            logger.error(e) {}
            throw e
        }
    }

    private fun processNewMonth(yearMonth: YearMonth) {
        try {
            previousMonthConnection?.close()
        } catch (e: SQLException) {
            // TODO: send to telegram
            logger.error(e) {}
            throw e
        }

        deleteTheOldestFile(yearMonth)

        previousMonthConnection = currentMonthConnection

        try {
            currentMonthConnection = DriverManager.getConnection(SQLITE + createPath(yearMonth))
        } catch (e: SQLException) {
            // TODO: send to telegram
            logger.error(e) {}
            throw e
        }

        currentYearMonth = yearMonth

        createTableIfNotExists()
    }

    private fun deleteTheOldestFile(yearMonth: YearMonth) {
        val file = File(createPath(yearMonth.minusMonths(numberOfFiles.toLong())))
        if (!file.exists()) {
            return
        }
        if (!file.delete()) {
            // TODO: send to telegram
            throw IOException("Failed to delete file: $file")
        }
    }

    fun closeConnections() {
        try {
            currentMonthConnection.close()
            previousMonthConnection?.close()
        } catch (e: SQLException) {
            logger.error(e) {}
            throw e
        }
    }
}