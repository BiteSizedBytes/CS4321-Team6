package campus;
import java.io.IOException;
import java.time.*;
import java.util.*;

/** Core rules have no dependency on the desktop UI. */
public final class ReservationService {
    private static final Comparator<Reservation> ORDER = Comparator.comparing(Reservation::date).thenComparing(Reservation::start).thenComparing(Reservation::spaceId);
    private final List<Space> spaces;
    private final ReservationRepository repository;
    private final Clock clock;
    private List<Reservation> reservations;
    public ReservationService(List<Space> spaces, ReservationRepository repository, Clock clock) throws IOException {
        this.spaces = List.copyOf(spaces); this.repository = repository; this.clock = clock;
        reservations = List.copyOf(repository.load());
        for (Reservation r : reservations) {
            if (space(r.spaceId()) == null || r.student().isBlank() || r.purpose().isBlank() || !r.end().isAfter(r.start()))
                throw new IOException("Stored reservation is invalid.");
            for (Reservation other : reservations)
                if (!r.id().equals(other.id()) && overlaps(r, other)) throw new IOException("Stored reservations overlap.");
        }
    }
    public List<Space> spaces(int minimumCapacity) {
        if (minimumCapacity < 0) throw new IllegalArgumentException("Capacity cannot be negative.");
        return spaces.stream().filter(s -> s.capacity() >= minimumCapacity).toList();
    }
    public Space space(String id) { return spaces.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null); }
    public List<Reservation> forDay(String spaceId, LocalDate date) {
        return reservations.stream().filter(r -> r.spaceId().equals(spaceId) && r.date().equals(date)).sorted(ORDER).toList();
    }
    public List<Reservation> forStudent(String student) {
        String owner = student == null ? "" : student.strip();
        return reservations.stream().filter(r -> r.student().equals(owner)).sorted(ORDER).toList();
    }
    public Reservation create(String student, String spaceId, String purpose, LocalDate date, LocalTime start, LocalTime end) throws IOException {
        return save(null, student, spaceId, purpose, date, start, end);
    }
    public Reservation update(UUID id, String student, String spaceId, String purpose, LocalDate date, LocalTime start, LocalTime end) throws IOException {
        owned(id, student);
        return save(id, student, spaceId, purpose, date, start, end);
    }
    private Reservation save(UUID id, String student, String spaceId, String purpose, LocalDate date, LocalTime start, LocalTime end) throws IOException {
        if (student == null || student.isBlank() || purpose == null || purpose.isBlank() || spaceId == null || date == null || start == null || end == null)
            throw new IllegalArgumentException("Student, space, purpose, date, start and end are required.");
        if (space(spaceId) == null) throw new IllegalArgumentException("Select a valid campus space.");
        if (!end.isAfter(start)) throw new IllegalArgumentException("End time must be after start time on the same day.");
        if (date.atTime(start).isBefore(LocalDateTime.now(clock))) throw new IllegalArgumentException("Reservations cannot start in the past.");
        Reservation candidate = new Reservation(id == null ? UUID.randomUUID() : id, spaceId, student.strip(), purpose.strip(), date, start, end);
        if (reservations.stream().anyMatch(r -> !r.id().equals(candidate.id()) && overlaps(r, candidate)))
            throw new IllegalArgumentException("This space already has a reservation overlapping that time.");
        List<Reservation> next = new ArrayList<>(reservations);
        next.removeIf(r -> r.id().equals(candidate.id())); next.add(candidate); commit(next); return candidate;
    }
    private static boolean overlaps(Reservation a, Reservation b) {
        return a.spaceId().equals(b.spaceId()) && a.date().equals(b.date()) && a.start().isBefore(b.end()) && b.start().isBefore(a.end());
    }
    public void cancel(UUID id, String student) throws IOException {
        owned(id, student); List<Reservation> next = new ArrayList<>(reservations);
        next.removeIf(r -> r.id().equals(id)); commit(next);
    }
    private void owned(UUID id, String student) {
        Reservation r = reservations.stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Reservation no longer exists."));
        if (student == null || !r.student().equals(student.strip())) throw new IllegalArgumentException("Choose a reservation belonging to the current student.");
    }
    private void commit(List<Reservation> next) throws IOException { repository.save(List.copyOf(next)); reservations = List.copyOf(next); }
}
