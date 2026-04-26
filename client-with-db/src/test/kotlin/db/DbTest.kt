package db

import model.Event
import model.EventType
import org.example.dateTimeFormatter
import org.example.db.Db
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.io.path.exists

class DbTest {

    private lateinit var db:Db

    @AfterEach
    fun tearDown() {
        if (::db.isInitialized) {
            db.closeConnections()
        }
    }

    @Test
    fun `Create file`(@TempDir tempDir: Path) {
        db = Db(
            pathWithoutFileName = tempDir.toAbsolutePath().toString() + File.separator,
            maxUnreceivedEvents = 1000,
            numberOfFiles = 5,
            currentYearMonth = YearMonth.of(2026, 4)
        )

        Assertions.assertTrue(tempDir.resolve("2026_04.db").exists())
    }

    @Test
    fun `Create file when write`(@TempDir tempDir: Path) {
        db = Db(
            pathWithoutFileName = tempDir.toAbsolutePath().toString() + File.separator,
            maxUnreceivedEvents = 1000,
            numberOfFiles = 5,
            currentYearMonth = YearMonth.of(2026, 4)
        )

        db.writeEvent(
            Event(100, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2026, 5)
        )

        Assertions.assertTrue(tempDir.resolve("2026_05.db").exists())
    }

    @Test
    fun `Remove file when numberOfFiles = 2`(@TempDir tempDir: Path) {
        db = Db(
            pathWithoutFileName = tempDir.toAbsolutePath().toString() + File.separator,
            maxUnreceivedEvents = 1000,
            numberOfFiles = 2,
            currentYearMonth = YearMonth.of(2026, 4)
        )

        db.writeEvent(
            Event(100, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2026, 5)
        )

        db.writeEvent(
            Event(101, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2026, 6)
        )

        Assertions.assertFalse(tempDir.resolve("2026_04.db").exists())
    }

    @Test
    fun `Remove file when numberOfFile more than 2`(@TempDir tempDir: Path) {
        db = Db(
            pathWithoutFileName = tempDir.toAbsolutePath().toString() + File.separator,
            maxUnreceivedEvents = 1000,
            numberOfFiles = 3,
            currentYearMonth = YearMonth.of(2025, 1)
        )

        db.writeEvent(
            Event(100, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2025, 2)
        )

        db.writeEvent(
            Event(100, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2025, 3)
        )
        
        db.writeEvent(
            Event(100, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2025,4)
        )

        Assertions.assertFalse(tempDir.resolve("2025_01.db").exists())
    }

    @Test
    fun `remove file when create file when write event`(@TempDir tempDir: Path) {
        db = Db(
            pathWithoutFileName = tempDir.toAbsolutePath().toString() + File.separator,
            maxUnreceivedEvents = 1000,
            numberOfFiles = 3,
            currentYearMonth = YearMonth.of(2025, 1)
        )

        db.writeEvent(
            Event(100, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2025, 2)
        )

        db.writeEvent(Event(100, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2025, 3)
        )

        db.writeEvent(Event(100, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2025, 4)
        )

        db.writeEvent(Event(100, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2025, 5)
        )

        Assertions.assertFalse(tempDir.resolve("2025_02.db").exists())
    }

    @Test
    fun `Remove file from previous year`(@TempDir tempDir: Path) {
        db = Db(
            pathWithoutFileName = tempDir.toAbsolutePath().toString() + File.separator,
            maxUnreceivedEvents = 1000,
            numberOfFiles = 2,
            currentYearMonth = YearMonth.of(2025, 11)
        )

        db.writeEvent(
            Event(100, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2025, 12)
        )

        db.writeEvent(
            Event(100, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2026, 1)
        )

        Assertions.assertFalse(tempDir.resolve("2025_11.db").exists())
        Assertions.assertTrue(tempDir.resolve("2025_12.db").exists())
    }

    @Test
    fun `write to file`(@TempDir tempDir: Path) {
        db = Db(
            pathWithoutFileName = tempDir.toAbsolutePath().toString() + File.separator,
            maxUnreceivedEvents = 1000,
            numberOfFiles = 2,
            currentYearMonth = YearMonth.of(2025, 11)
        )

        db.writeEvent(
            Event(100, EventType.START, LocalDateTime.now().format(dateTimeFormatter)),
            YearMonth.of(2025, 11)
        )

        Assertions.assertEquals(100, db.getLastId())
    }

    @Test
    fun `open already created file`(@TempDir tempDir: Path) {
        db = Db(
            pathWithoutFileName = tempDir.toAbsolutePath().toString() + File.separator,
            maxUnreceivedEvents = 1000,
            numberOfFiles = 2,
            currentYearMonth = YearMonth.of(2025, 11)
        )

        db.writeEvent(
            Event(100, EventType.PING, LocalDateTime.now().format(dateTimeFormatter)),
            yearMonth = YearMonth.of(2025, 12)
        )

        db.closeConnections()

        db = Db(
            pathWithoutFileName = tempDir.toAbsolutePath().toString() + File.separator,
            maxUnreceivedEvents = 1000,
            numberOfFiles = 2,
            currentYearMonth = YearMonth.of(2025, 12)
        )

        Assertions.assertEquals(100, db.getLastId())
    }

    @Test
    fun `last Id == 0 when there are no records in the db`(@TempDir tempDir: Path) {
        db = Db(
            pathWithoutFileName = tempDir.toAbsolutePath().toString() + File.separator,
            maxUnreceivedEvents = 1000,
            numberOfFiles = 2,
            currentYearMonth = YearMonth.of(2026, 4)
        )

        Assertions.assertEquals(0, db.getLastId())
    }
}