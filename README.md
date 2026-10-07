# Campus Space Reservations

A standalone Java Swing application that demonstrates campus space discovery, availability checking, and reservation management. The proof of concept is based on user stories US-1 through US-10.

The application runs locally, stores reservations in a properties file, and requires no server connection or external libraries.

## Requirements

* JDK 17 or newer
* IntelliJ IDEA (optional)
* PowerShell for the provided run and test scripts on Windows

## Running the Application

## Option 1: IntelliJ IDEA

1. Open the project in IntelliJ IDEA.
2. Ensure the project is configured to use a compatible JDK.
3. Open src/Main.java.
4. Run Main.main().

## Option 2: PowerShell

From the project root, run:

`powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/run.ps1`

The script checks JAVA_HOME, the existing IntelliJ JDK at ~/.jdks/openjdk-27, or Java available on PATH.

To use a different reservation data file, pass the -DataFile parameter:

`./scripts/run.ps1 -DataFile 'data/demo.properties'`

## Using the Application

1. Identify the student. Enter a student name or ID. Use the same value to view previously created reservations. Leading and trailing whitespace is ignored, but capitalization matters. This identifier is not an authentication mechanism.

2. Browse campus spaces. View available spaces, buildings, and capacities. Select a space to see its features and other details. Filter the list by minimum capacity or clear the filter to display all spaces.

3. Check availability. Select a date from the monthly calendar. An asterisk indicates that the selected space has reservations on that date. The daily schedule displays available and reserved time blocks, with separate colors for each.

4. Create a reservation. Select a space and click Create reservation. Enter the purpose, date, start time, and end time.

5. Manage reservations. Open My reservations to view bookings associated with the entered student identifier. Select a reservation to modify or cancel it. Cancellation requires confirmation.

## Reservation Rules and Limitations

* Every reservation requires a student identifier, valid campus space, purpose, date, start time, and end time.
* Reservations cannot start in the past, according to the machine's local clock.
* The end time must be strictly later than the start time on the same date. Overnight reservations are not supported.
* Times use the 24-hour HH:mm format. Valid input times range from 00:00 through 23:59; 24:00 is not accepted as an input time.
* Reservation intervals include their start time and exclude their end time. For example, a reservation from 10:00 to 11:00 can be followed by one from 11:00 to 12:00.
* Overlapping reservations for the same space on the same date are rejected during both creation and modification.
* The application includes four sample spaces defined in model/Space.java. Space administration, authentication, recurring reservations, and campus operating-hour restrictions are outside the scope of this proof of concept.
* All times within a date are treated as reservable, subject to existing reservations and the rule against starting in the past.
* Run only one application instance against a given data file. Concurrent access and coordination between separate installations are not supported.

## Data Storage

Reservations are stored locally in data/reservations.properties, relative to the application's working directory by default. The active data file's absolute path is displayed at the bottom of the application window.

Saved reservations persist when the application closes and reopens using the same data file. A different data-file path can be supplied through the run script or as the application's first command-line argument.

The repository writes updated data to a temporary file before replacing the existing file, using an atomic move when supported by the filesystem. The service updates its in-memory reservation list only after the repository save succeeds.

Invalid stored data causes a startup error rather than being silently discarded or overwritten. Application data and compiled files are excluded from Git.

## Project Structure

| **File** | **Responsibility** |
| --- | --- |
| `src/Main.java`	| Application entry point, initialization, and dependency setup |
| `src/model/Space.java` | Campus space information and sample spaces |
| `src/model/Reservation.java` | Immutable reservation data record |
| `src/model/ScheduleBlock.java` | Available and reserved schedule intervals |
| `src/controller/ReservationService.java` | Reservation validation, queries, ownership checks, and conflict prevention |
| `src/persistence/ReservationRepository.java` | Interface defining reservation loading and saving |
| `src/persistence/FileReservationRepository.java` | Local properties-file persistence |
| `src/view/ReservationWindow.java` | Swing interface, calendar, schedule, and reservation forms |
| `scripts/run.ps1` | Application launch script |
| `scripts/test.ps1` | Automated test runner |
| `tests/` | Automated reservation acceptance checks |

The reservation service is independent of the desktop interface. Its repository interface allows persistence to be substituted, while its injectable Clock allows time-dependent rules to be tested with a fixed clock.

## Testing

Run the automated tests from the project root:

`powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/test.ps1`

The dependency-free test runner checks reservation discovery and filtering, empty results, ordering, overlapping and adjacent bookings, required fields, past dates and times, modifications, ownership, cancellation, failed writes, persistence, empty data, and corrupt-file handling.

Tests use temporary storage and do not modify the application's normal reservation data.

## Manual Acceptance Checks

| **User stories** | **Verification** |
| --- | --- |
| US-1–US-3 | Launch the application and verify space names, buildings, capacities, and features. Set the minimum capacity to 12, then 1000, and finally clear the filter. |
| US-4–US-5 | Select a space and a future date. Create a 10:00–11:00 reservation. Verify schedule labels, colors, chronological ordering, and the calendar marker. Select an unbooked date and verify full availability. |
| US-6 | Attempt a conflicting 10:30–11:30 reservation and verify rejection. Create an adjacent 11:00–12:00 reservation and verify acceptance. Test missing purposes, reversed times, and past dates. |
| US-7–US-8 | Open My reservations, verify ordering, modify a reservation to a free time, and attempt a conflicting modification. Change the student identifier and verify that the displayed reservations change accordingly. |
| US-9 | Begin cancellation and select No to verify that the reservation remains. Repeat and select Yes to verify cancellation and updated availability. |
| US-10 | Close and reopen the application using the same data file and verify that reservations persist. Cancel all reservations for the test student, restart the application, and verify that the empty list remains empty. |

## Scope

This project is a local desktop proof of concept intended to demonstrate the core reservation workflow. It does not provide user authentication, a centralized database, multi-user synchronization, or administrative management of campus spaces.
