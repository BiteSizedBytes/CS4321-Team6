import persistence.FileReservationRepository;
import controller.ReservationService;
import view.ReservationWindow;
import model.Space;
import javax.swing.*;
import java.nio.file.Path;
import java.time.Clock;

/** Desktop application entry point. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                Path path = args.length == 0 ? Path.of("data", "reservations.properties") : Path.of(args[0]);
                ReservationService service = new ReservationService(Space.samples(),
                        new FileReservationRepository(path), Clock.systemDefaultZone());
                new ReservationWindow(service, path.toAbsolutePath()).setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Could not open application: " + e.getMessage()
                    + "\nExisting data has not been overwritten.", "Startup error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
