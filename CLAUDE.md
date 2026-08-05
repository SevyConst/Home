# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git

NEVER commit, NEVER push, even if a skill instructs you to.

## Build & test

Gradle 9.4 wrapper, JVM toolchain 25 for every module.

```bash
./gradlew build                       # compile + test everything
./gradlew :server:test                # one module's tests
./gradlew :server:bootRun             # run the server (needs the env vars below)
```

Runtime configuration is entirely environment variables; there are no committed `.env` files (`*.env` and `*.db` are gitignored).

- Server: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `TELEGRAM_BOT_TOKEN`, `DISPLAY_ZONE`.
- Clients: `SERVER_URL`, `CONNECT_TIMEOUT`, `REQUEST_TIMEOUT`, `DEVICE_ID`; `client-with-db` additionally needs `URL_DB` (path prefix, not a file), `MAX_UNRECEIVED_EVENTS`, `NUMBER_OF_FILES`.

## The protocol

`Event.time` is ISO-8601 **with offset** (`2026-08-02T21:48:00+03:00`), so each event is a self-contained instant independent of how the device's OS timezone is set. Ids are monotonic per device and assigned by the client.

The ping period is deliberately not configurable. It lives in `core` as `PING_PERIOD_MILLISECONDS` (`org.example.ConstantsKt`, one minute) and is read by both clients' schedulers and by `EventService.periodMilliseconds`, which `DeviceProcessor` multiplies by `COEFFICIENT_WAITING` to get the offline threshold. 

`client-with-db` writes each event to SQLite before sending. Files are one per month, named `<URL_DB><yyyy>_<MM>.db`; `EventDb` keeps at most two connections open (current + previous month) and deletes the file `NUMBER_OF_FILES` months back on rollover. On each tick it reads the unreceived tail backwards, sends the batch oldest-first, and marks rows received only after a 2xx. So the server sees the whole backlog after an outage — that backlog is exactly the evidence it needs. Any DB failure in the client is fatal: it POSTs an `ERROR` event and calls `exitProcess(1)`.

## Server architecture

`EventController` → `EventService.processEvents(EventRequest)` → Telegram.

Per-device state lives in `EventService.deviceIdToDeviceProcessorMap` (`ConcurrentHashMap<String, DeviceProcessor>`), not in the database. `DeviceProcessor` holds a `ReentrantLock` that serializes everything for a device, plus `lastOnlineTime`, `isOffline`, the countdown timer, and `chatIdToRepliedMessageIdMap` (used to thread the recovery message as a Telegram reply to the "device unreachable" message).

Two patterns in there are load-bearing and easy to break:

- **Removal race.** `processEvents` takes the lock, then re-checks `deviceProcessor.isRemoved()` and `continue`s the `while(true)` loop if set. A processor for an unknown device is marked removed, evicted, and shut down while the lock is held — the retry loop is what keeps a concurrent request from using a dead processor.
- **Timer generation guard.** `startCountdownTimer()` (lock must be held) cancels the previous task and bumps `generation`; the scheduled body re-acquires the lock and returns unless its captured generation still matches. The timer is one-shot and fires at `periodMilliseconds * 2`, marking the device offline and announcing it.

Persistent state is PostgreSQL, reached only through `DeviceRepository` via `JdbcTemplate`. The schema is not in the repo — it is defined implicitly by the SQL there: `device(id, has_clock, has_error)`, `person(name, chat_id, is_admin)`, `person_device(device_id, person_name)`. A device whose events include an `ERROR` gets `has_error = true` and is then ignored on every later request until the flag is cleared by hand.

`has_clock` is the other branch that runs through everything: devices without a working clock get times from `ZonedDateTime.now(clock)` instead of from `Event.time`, and their multi-event reports count restarts rather than listing intervals.

### Time

Never call `ZonedDateTime.now()` or `Clock.systemDefaultZone()` in server code. Inject the `Clock` bean from `Config` — it is pinned to `app.display-zone` because the server runs in a different zone from the devices, and every `ZonedDateTime` in `EventService` must be on that one scale. Event times are converted with `atZoneSameInstant`, never by relabelling local fields.

### Message text

User-facing strings are Russian `public static final` constants on `EventService`.

## Tests

- `server`: `EventServiceTest` drives `EventService` directly with Mockito mocks for `TelegramNotifier`, `DeviceRepository`, and `Clock`, scripting `clockMock.instant()`/`getZone()` between calls to simulate time passing. Offline-path tests shrink the static `EventService.periodMilliseconds` to 100ms and really sleep, restoring the old value in `@AfterEach` — they are genuinely timing-sensitive, so a flake there is usually load, not a regression.
- `client-with-db`: JUnit 5 + mockito-kotlin, real SQLite files under `@TempDir`. Always `closeConnections()` in teardown.
