package campus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
public record Reservation(UUID id, String spaceId, String student, String purpose,
                          LocalDate date, LocalTime start, LocalTime end) { }
