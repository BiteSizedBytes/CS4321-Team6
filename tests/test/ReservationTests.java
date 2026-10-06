package test;

import controller.ReservationService;
import model.Reservation;
import model.Space;
import persistence.FileReservationRepository;
import persistence.ReservationRepository;
import model.ScheduleBlock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.List;

/** Dependency-free acceptance tests. Run with scripts/test.ps1. */
public final class ReservationTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T09:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate DAY = LocalDate.of(2030, 1, 2);
    private static int checks;
    private static final class MemoryRepository implements ReservationRepository {
        List<Reservation> data = List.of();
        boolean fail;
        public List<Reservation> load() { return data; }
        public void save(List<Reservation> next) throws IOException {
            if (fail) throw new IOException("Simulated disk failure");
            data = List.copyOf(next);
        }
    }
    public static void main(String[] args) throws Exception {
        MemoryRepository repository = new MemoryRepository();
        ReservationService s = new ReservationService(Space.samples(), repository, CLOCK);
        check(s.spaces(0).size() == 4, "US-1 list spaces");
        check(
                s.spaces(0).stream().allMatch(space ->
                        !space.name().isBlank()
                        && !space.building().isBlank()
                        && space.capacity() > 0
        ),
        "US-1 spaces include name,building, and capacity"
        );
        check(s.space("library-101").features().contains("Whiteboard"), "US-2 details");
        check(s.space("unknown") == null, "US-2 missing space");
        check(s.spaces(12).size() == 3 && s.spaces(12).stream().allMatch(v -> v.capacity() >= 12), "US-3 inclusive filter");
        check(s.spaces(1000).isEmpty() && s.spaces(0).size() == 4, "US-3 empty and clear");
        check(new ReservationService(List.of(), new MemoryRepository(), CLOCK).spaces(0).isEmpty(), "US-1 empty catalog");
        check(s.forDay("library-101", DAY).isEmpty(), "US-4 fully available");
        MemoryRepository scheduleRepository = new MemoryRepository();
        ReservationService scheduleService = new ReservationService(Space.samples(),scheduleRepository, CLOCK);
        List<ScheduleBlock> emptySchedule = scheduleService.scheduleForDay("library-101", DAY);

        check(
                emptySchedule.size() == 1
                        && !emptySchedule.get(0).reserved()
                        && emptySchedule.get(0).start().equals(DAY.atStartOfDay())
                        && emptySchedule.get(0).end().equals(DAY.plusDays(1).atStartOfDay()),
                "US-5 fully available schedule block"
        );
        create(scheduleService, "alice", "library-101", DAY, "10:00", "11:00");

        List<ScheduleBlock> oneReservationSchedule = scheduleService.scheduleForDay("library-101", DAY);

        check(
                oneReservationSchedule.size() == 3,
                "US-5 schedule splits around reservation"
        );

        check(
                !oneReservationSchedule.get(0).reserved()
                        && oneReservationSchedule.get(0).start().equals(DAY.atStartOfDay())
                        && oneReservationSchedule.get(0).end().equals(DAY.atTime(10, 0)),
                "US-5 available block before reservation"
        );

        check(
                oneReservationSchedule.get(1).reserved()
                        && oneReservationSchedule.get(1).start().equals(DAY.atTime(10, 0))
                        && oneReservationSchedule.get(1).end().equals(DAY.atTime(11, 0))
                        && oneReservationSchedule.get(1).purpose().equals("Study"),
                "US-5 reserved schedule block"
        );

        check(
                !oneReservationSchedule.get(2).reserved()
                        && oneReservationSchedule.get(2).start().equals(DAY.atTime(11, 0))
                        && oneReservationSchedule.get(2).end().equals(DAY.plusDays(1).atStartOfDay()),
                "US-5 available block after reservation"
        );
        create(scheduleService, "alice", "library-101", DAY, "11:00", "12:00");

        List<ScheduleBlock> backToBackSchedule =
                scheduleService.scheduleForDay("library-101", DAY);

        check(
                backToBackSchedule.size() == 4
                        && backToBackSchedule.get(1).reserved()
                        && backToBackSchedule.get(2).reserved()
                        && backToBackSchedule.get(1).end()
                        .equals(backToBackSchedule.get(2).start()),
                "US-5 back-to-back reservations have no available gap"
        );
        Reservation first = create(s, "alice", "library-101", DAY, "10:00", "11:00");
        reject(() -> create(s, "bob", "library-101", DAY, "10:30", "11:30"), "US-6 overlap");
        reject(() -> create(s, "bob", "library-101", DAY, "09:00", "12:00"), "US-6 enclosing overlap");
        reject(() -> create(s, "bob", "library-101", DAY, "10:10", "10:20"), "US-6 contained overlap");
        Reservation second = create(s, "alice", "library-101", DAY, "11:00", "12:00");
        create(s, "bob", "library-101", DAY, "09:00", "10:00");
        create(s, "bob", "library-202", DAY, "10:00", "11:00");
        check(s.forDay("library-101", DAY).get(0).start().equals(LocalTime.of(9, 0)), "US-4 sorted daily schedule");
        reject(() -> create(s, "alice", "library-101", DAY, "13:00", "12:00"), "US-6 reversed times");
        reject(() -> create(s, "alice", "library-101", DAY, "13:00", "13:00"), "US-6 zero duration");
        reject(() -> create(s, "alice", "library-101", DAY.minusDays(2), "13:00", "14:00"), "US-6 past date");
        reject(() -> create(s, "alice", "library-101", DAY.minusDays(1), "08:00", "10:00"), "US-6 past time today");
        reject(() -> create(s, " ", "library-101", DAY, "13:00", "14:00"), "US-6 missing student");
        reject(() -> s.create("alice", "library-101", "", DAY, LocalTime.NOON, LocalTime.of(13, 0)), "US-6 missing purpose");
        reject(() -> s.create("alice", "library-101", "Study", null, LocalTime.NOON, LocalTime.of(13, 0)), "US-6 missing date");
        reject(() -> s.create("alice", "library-101", "Study", DAY, null, LocalTime.of(13, 0)), "US-6 missing time");
        reject(() -> create(s, "alice", "unknown", DAY, "13:00", "14:00"), "US-6 invalid space");
        check(s.forStudent("alice").equals(List.of(first, second)), "US-7 sorted own reservations");
        check(s.forStudent("new-student").isEmpty(), "US-7 empty personal list");
        create(s, "alice", "library-101", DAY.plusDays(1), "08:00", "09:00");
        check(s.forStudent("alice").get(2).date().equals(DAY.plusDays(1)), "US-7 date sorting");
        Reservation updated = s.update(first.id(), "alice", first.spaceId(), "Club meeting", DAY, LocalTime.of(13, 0), LocalTime.of(14, 0));
        check(updated.id().equals(first.id()) && s.forStudent("alice").contains(updated), "US-8 update retains identity");
        s.update(updated.id(), "alice", updated.spaceId(), updated.purpose(), DAY, updated.start(), updated.end());
        reject(() -> s.update(first.id(), "alice", first.spaceId(), "Study", DAY, LocalTime.of(11, 30), LocalTime.of(12, 30)), "US-8 conflicting modification");
        reject(() -> s.update(first.id(), "alice", first.spaceId(), "Study", DAY, LocalTime.of(15, 0), LocalTime.of(14, 0)), "US-8 reversed modification");
        reject(() -> s.update(first.id(), "alice", first.spaceId(), "Study", DAY.minusDays(2), LocalTime.NOON, LocalTime.of(13, 0)), "US-8 past modification");
        reject(() -> s.cancel(first.id(), "bob"), "Ownership enforced");
        check(s.forStudent("alice").contains(updated), "Failed edits preserve previous data");
        repository.fail = true;
        ioReject(() -> create(s, "alice", "library-101", DAY, "15:00", "16:00"));
        ioReject(() -> s.cancel(first.id(), "alice"));
        ioReject(() -> s.update(first.id(), "alice", first.spaceId(), "Study", DAY, LocalTime.of(15, 0), LocalTime.of(16, 0)));
        check(s.forStudent("alice").contains(updated) && s.forStudent("alice").size() == 3, "Failed saves leave state intact");
        repository.fail = false;
        s.cancel(first.id(), "alice");
        create(s, "bob", "library-101", DAY, "13:00", "14:00");
        check(s.forStudent("alice").stream().noneMatch(r -> r.id().equals(first.id())), "US-9 cancellation releases slot");
        persistence();
        System.out.println("Passed " + checks + " acceptance checks.");
    }
    private static void persistence() throws Exception {
        Path directory = Files.createTempDirectory("campus-reservation-tests-");
        Path path = directory.resolve("reservations.properties");
        try {
            ReservationService first = new ReservationService(Space.samples(), new FileReservationRepository(path), CLOCK);
            check(first.forStudent("alice").isEmpty(), "US-10 first run");
            Reservation r = first.create("alice", "library-101", "Study = math, café\nClub", DAY, LocalTime.of(10, 0), LocalTime.of(11, 0));
            ReservationService reopened = new ReservationService(Space.samples(), new FileReservationRepository(path), CLOCK);
            check(reopened.forStudent("alice").equals(List.of(r)), "US-10 complete persistence round trip");
            reopened.update(r.id(), "alice", r.spaceId(), "Updated", DAY, LocalTime.NOON, LocalTime.of(13, 0));
            reopened = new ReservationService(Space.samples(), new FileReservationRepository(path), CLOCK);
            check(reopened.forStudent("alice").get(0).purpose().equals("Updated"), "US-10 persist modification");
            reopened.cancel(r.id(), "alice");
            check(new ReservationService(Space.samples(), new FileReservationRepository(path), CLOCK).forStudent("alice").isEmpty(), "US-10 persist empty after cancellation");
            Files.writeString(path, "broken file");
            ioReject(() -> new ReservationService(Space.samples(), new FileReservationRepository(path), CLOCK));
            check(Files.readString(path).equals("broken file"), "Corrupt file is not overwritten");
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(directory); }
    }
    private static Reservation create(ReservationService s, String student, String space, LocalDate day, String start, String end) throws IOException {
        return s.create(student, space, "Study", day, LocalTime.parse(start), LocalTime.parse(end));
    }
    private interface Action { void run() throws Exception; }
    private static void reject(Action action, String label) throws Exception {
        try { action.run(); throw new AssertionError(label + " should be rejected"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void ioReject(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected storage failure"); }
        catch (IOException expected) { checks++; }
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label); checks++;
    }
}
