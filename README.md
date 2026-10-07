# Campus Space Reservations

A standalone Java Swing proof of concept based on US-1 through US-10 in the supplied spreadsheet. Everything runs locally; there are no server connections or external libraries. Requires JDK 17 or newer.

## Run

In IntelliJ, open `src/Main.java` and run `Main.main()`. The project already uses a local JDK.

Or from PowerShell in the project directory:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/run.ps1
```

The scripts use `JAVA_HOME`, the existing IntelliJ JDK at `~/.jdks/openjdk-27`, or Java on PATH. Optionally choose a different data file with `./scripts/run.ps1 -DataFile 'data/demo.properties'`.

1. Enter a student name or ID. Use the same exact value each time to view your reservations. Leading and trailing spaces are ignored; capitalization matters. This identifies reservations locally and is not authentication.
2. Browse the sample spaces, filter by minimum capacity, and select a row for details.
3. Click a day in the month calendar. An asterisk indicates that the selected space has bookings that day. The daily schedule labels and colors available and reserved periods.
4. Choose **Create reservation** and enter a purpose, date, and start/end times.
5. Open **My reservations**, select a booking, then modify it or cancel it. Cancellation requires confirmation.

Reservations save immediately to `data/reservations.properties`, relative to the working directory. Reopening the application from the same directory restores them. The file location is shown at the bottom of the window. Local data and compiled files are ignored by Git.

## Rules and scope

- A reservation needs a student, known space, purpose, date, start and end. Start times in the past are rejected using the machine's local clock.
- End must be strictly after start on the same date. Overnight bookings and `24:00` as an input are outside this prototype. The schedule uses `24:00` to label the end of the day.
- Time intervals use an inclusive start and exclusive end: 10:00–11:00 and 11:00–12:00 are allowed. Overlaps in the same room on the same date are rejected for both creation and modification.
- There are four sample spaces defined in `Space.samples()`. No space administration, login, recurring bookings, or campus operating-hour policies were requested. All times on a day are treated as reservable.
- Use one running instance for a given data file. Sharing or coordinating separate installations is out of scope.
- Saves write to a temporary file, then replace the data file (atomically where supported). Failed saves do not alter the service's in-memory state. Invalid stored data produces a startup error and is not silently erased.

## Architecture

| File | Responsibility |
| --- | --- |
| `src/Main.java` | Application startup and composition |
| `src/campus/Space.java`, `Reservation.java` | Immutable domain records |
| `src/campus/ReservationService.java` | Filtering, queries, ownership, validation and conflicts |
| `src/campus/ReservationRepository.java` | Storage interface |
| `src/campus/FileReservationRepository.java` | Local properties-file storage |
| `src/campus/ReservationWindow.java` | Swing views and input handling |
| `tests/campus/ReservationTests.java` | Automated acceptance checks with a fixed clock |

The repository interface and injectable clock let the core rules be tested without opening the desktop UI or touching real reservation data.

## Verify

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/test.ps1
```

The dependency-free test runner checks discovery/filtering, empty lists, ordering, overlapping and adjacent bookings, required fields, past times, modifications, ownership, cancellation, failed writes, persistence of changes and empty data, and corrupt-file handling. Tests use temporary storage and do not change application data.

Manual UI acceptance checks:

| Stories | Check |
| --- | --- |
| US-1–US-3 | Open the app; verify names, buildings and capacities. Select a space for features. Set capacity to 12, then 1000, then clear it. |
| US-4–US-5 | Select a room and future day. Create a 10:00–11:00 booking. Confirm reserved/free labels and colors, chronological order, and the calendar marker. Select an unbooked day and confirm fully available. |
| US-6 | Try 10:30–11:30 (reject) and 11:00–12:00 (accept). Try missing purpose, reversed times and past dates. |
| US-7–US-8 | Open My reservations, check ordering, modify to a free time, then attempt a conflicting time. Switch student ID and confirm the list changes. |
| US-9 | Start cancellation and choose No; confirm the booking remains. Choose Yes and confirm the slot becomes available. |
| US-10 | Close/reopen from the same directory and confirm bookings persist. Cancel all your bookings, close/reopen, and confirm the list stays empty. |
