package campus;
import java.io.IOException;
import java.util.List;
public interface ReservationRepository {
    List<Reservation> load() throws IOException;
    void save(List<Reservation> reservations) throws IOException;
}
