package campus;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Writes a temporary file before replacing the saved data. */
public final class FileReservationRepository implements ReservationRepository {
    private final Path file;
    public FileReservationRepository(Path file) { this.file = file.toAbsolutePath(); }
    public List<Reservation> load() throws IOException {
        if (!Files.exists(file)) return List.of();
        Properties data = new Properties();
        try {
            try (Reader reader = Files.newBufferedReader(file)) { data.load(reader); }
            if (!"1".equals(data.getProperty("version"))) throw new IllegalArgumentException("Unsupported data version");
            int count = Integer.parseInt(required(data, "count"));
            if (count < 0) throw new IllegalArgumentException("Invalid count");
            List<Reservation> result = new ArrayList<>();
            Set<UUID> ids = new HashSet<>();
            for (int i = 0; i < count; i++) {
                String p = "reservation." + i + ".";
                Reservation r = new Reservation(UUID.fromString(required(data, p + "id")), required(data, p + "space"),
                    required(data, p + "student"), required(data, p + "purpose"), LocalDate.parse(required(data, p + "date")),
                    LocalTime.parse(required(data, p + "start")), LocalTime.parse(required(data, p + "end")));
                if (!ids.add(r.id()) || !r.end().isAfter(r.start())) throw new IllegalArgumentException("Invalid reservation");
                result.add(r);
            }
            return List.copyOf(result);
        } catch (RuntimeException e) { throw new IOException("Invalid reservation file: " + file + " (" + e.getMessage() + ")", e); }
    }
    private static String required(Properties data, String key) {
        String v = data.getProperty(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return v;
    }
    public void save(List<Reservation> reservations) throws IOException {
        Properties data = new Properties();
        data.setProperty("version", "1");
        data.setProperty("count", Integer.toString(reservations.size()));
        for (int i = 0; i < reservations.size(); i++) {
            Reservation r = reservations.get(i);
            String p = "reservation." + i + ".";
            data.setProperty(p + "id", r.id().toString());
            data.setProperty(p + "space", r.spaceId());
            data.setProperty(p + "student", r.student());
            data.setProperty(p + "purpose", r.purpose());
            data.setProperty(p + "date", r.date().toString());
            data.setProperty(p + "start", r.start().toString());
            data.setProperty(p + "end", r.end().toString());
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "reservations-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) { data.store(writer, "Campus reservations - local proof of concept"); }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
