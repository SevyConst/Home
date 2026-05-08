import model.Event
import model.EventRequest
import model.EventType
import org.example.HttpSender
import org.example.db.EventDb
import org.example.processEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path
import java.time.YearMonth

class ProcessEventTest {

    private lateinit var eventDb: EventDb

    @AfterEach
    fun tearDown() {
        if (::eventDb.isInitialized) {
            eventDb.closeConnections()
        }
    }

    @Test
    fun `processEvent sends and marks received events from previous and current month`(@TempDir tempDir: Path) {
        val firstMonth = YearMonth.of(2026, 4)
        eventDb = EventDb(
            pathWithoutFileName = tempDir.toAbsolutePath().toString() + File.separator,
            maxUnreceivedEvents = 1000,
            numberOfFiles = 5,
            currentYearMonth = firstMonth
        )

        eventDb.writeEvent(
            Event(1L, EventType.PING, "time"),
            firstMonth
        )

        val secondMonth = YearMonth.of(2026, 5)

        val httpSender: HttpSender = mock()
        whenever(httpSender.send(any())).thenReturn(true)

        val eventDbSpy = spy(eventDb)
        processEvent(
            event = Event(2L, EventType.PING, "time"),
            yearMonth = secondMonth,
            eventDb = eventDbSpy,
            deviceId = "deviceId",
            httpSender = httpSender
        )
        verify(eventDbSpy).markReceived(any(), eq(true))

        val requestCaptor = argumentCaptor<EventRequest>()
        verify(httpSender).send(requestCaptor.capture())
        Assertions.assertEquals(listOf(1L, 2L), requestCaptor.firstValue.events.map { it.id })

        val collected = mutableListOf<Event>()
        val isPreviousConnectionUsed = eventDb.readUnreceivedTail(collected)
        Assertions.assertFalse(isPreviousConnectionUsed)
        Assertions.assertTrue(collected.isEmpty())
    }

    @Test
    fun `processEvent sends and marks received events from current month only`(@TempDir tempDir: Path) {
        val firstMonth = YearMonth.of(2026, 4)
        eventDb = EventDb(
            pathWithoutFileName = tempDir.toAbsolutePath().toString() + File.separator,
            maxUnreceivedEvents = 1000,
            numberOfFiles = 5,
            currentYearMonth = firstMonth
        )

        eventDb.writeEvent(
            Event(1L, EventType.PING, "time"),
            firstMonth
        )

        val secondMonth = YearMonth.of(2026, 5)

        eventDb.writeEvent(
            Event(2L, EventType.PING, "time"),
            secondMonth
        )

        eventDb.markReceived(listOf(1L, 2L), false)

        val httpSender: HttpSender = mock()
        whenever(httpSender.send(any())).thenReturn(true)

        val eventDbSpy = spy(eventDb)
        processEvent(
            event = Event(3L, EventType.PING, "time"),
            yearMonth = secondMonth,
            eventDb = eventDbSpy,
            deviceId = "deviceId",
            httpSender = httpSender
        )
        verify(eventDbSpy).markReceived(any(), eq(false))

        val requestCaptor = argumentCaptor<EventRequest>()
        verify(httpSender).send(requestCaptor.capture())
        Assertions.assertEquals(listOf(3L), requestCaptor.firstValue.events.map { it.id })
    }

}
