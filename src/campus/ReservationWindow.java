package campus;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ReservationWindow extends JFrame {
    private final ReservationService service;
    private final JTextField student = new JTextField(18);
    private final JSpinner capacity = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));
    private final DefaultTableModel spaceModel = model("Space", "Building", "Capacity");
    private final DefaultTableModel scheduleModel = model("Start", "End", "Availability", "Purpose");
    private final DefaultTableModel mineModel = model("Date", "Start", "End", "Space", "Purpose");
    private final JTable spaces = new JTable(spaceModel);
    private final JTable schedule = new JTable(scheduleModel);
    private final JTable mine = new JTable(mineModel);
    private final JLabel details = new JLabel("Select a space to view its details.");
    private final JLabel spaceMessage = new JLabel();
    private final JLabel scheduleMessage = new JLabel();
    private final JLabel mineMessage = new JLabel();
    private final JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
    private final JPanel days = new JPanel(new GridLayout(0, 7, 3, 3));
    private final JButton create = new JButton("Create reservation");
    private final JButton edit = new JButton("Modify selected");
    private final JButton cancel = new JButton("Cancel selected");
    private List<Space> visibleSpaces = List.of();
    private List<Reservation> myReservations = List.of();
    private LocalDate selectedDate = LocalDate.now();
    private YearMonth month = YearMonth.from(selectedDate);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public ReservationWindow(ReservationService service, Path dataFile) {
        super("Campus Space Reservations");
        this.service = service;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1120, 760);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        setContentPane(root);
        JPanel identity = new JPanel(new FlowLayout(FlowLayout.LEFT));
        identity.add(new JLabel("Student name / ID:")); identity.add(student);
        identity.add(new JLabel("Use the same name / ID to find your saved reservations."));
        root.add(identity, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Spaces & calendar", browsePanel());
        tabs.addTab("My reservations", minePanel());
        root.add(tabs, BorderLayout.CENTER);
        root.add(new JLabel("Local data: " + dataFile), BorderLayout.SOUTH);
        student.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshMine(); }
            public void removeUpdate(DocumentEvent e) { refreshMine(); }
            public void changedUpdate(DocumentEvent e) { refreshMine(); }
        });
        refreshSpaces(); refreshMine();
    }

    private JPanel browsePanel() {
        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.setBorder(BorderFactory.createTitledBorder("Campus spaces"));
        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filter.add(new JLabel("Minimum capacity:")); filter.add(capacity);
        JButton clear = new JButton("Clear"); filter.add(clear);
        clear.addActionListener(e -> capacity.setValue(0));
        capacity.addChangeListener(e -> refreshSpaces());
        left.add(filter, BorderLayout.NORTH);
        left.add(new JScrollPane(spaces), BorderLayout.CENTER);
        JPanel info = new JPanel(new GridLayout(2, 1));
        info.add(spaceMessage); info.add(details); left.add(info, BorderLayout.SOUTH);
        spaces.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        spaces.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) refreshDay(); });

        JPanel right = new JPanel(new BorderLayout(8, 8));
        JPanel calendar = new JPanel(new BorderLayout(4, 4));
        calendar.setBorder(BorderFactory.createTitledBorder("Select a day"));
        JPanel navigation = new JPanel(new BorderLayout());
        JButton previous = new JButton("<"); JButton next = new JButton(">");
        previous.addActionListener(e -> { month = month.minusMonths(1); refreshCalendar(); });
        next.addActionListener(e -> { month = month.plusMonths(1); refreshCalendar(); });
        navigation.add(previous, BorderLayout.WEST); navigation.add(monthLabel, BorderLayout.CENTER); navigation.add(next, BorderLayout.EAST);
        calendar.add(navigation, BorderLayout.NORTH); calendar.add(days, BorderLayout.CENTER);
        right.add(calendar, BorderLayout.NORTH);
        schedule.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                if (!selected) {
                    c.setBackground("Reserved".equals(table.getValueAt(row, 2)) ? new Color(255, 222, 200) : new Color(218, 242, 224));
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });
        schedule.setRowHeight(28);
        right.add(new JScrollPane(schedule), BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout()); footer.add(scheduleMessage, BorderLayout.CENTER);
        footer.add(create, BorderLayout.SOUTH); right.add(footer, BorderLayout.SOUTH);
        create.addActionListener(e -> openForm(null));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right); split.setResizeWeight(0.48);
        JPanel panel = new JPanel(new BorderLayout()); panel.add(split); return panel;
    }

    private JPanel minePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(mineMessage, BorderLayout.NORTH); panel.add(new JScrollPane(mine), BorderLayout.CENTER);
        mine.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mine.getSelectionModel().addListSelectionListener(e -> refreshButtons());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT)); actions.add(edit); actions.add(cancel);
        panel.add(actions, BorderLayout.SOUTH);
        edit.addActionListener(e -> { Reservation r = selectedReservation(); if (r != null) openForm(r); });
        cancel.addActionListener(e -> {
            Reservation r = selectedReservation(); if (r == null) return;
            int answer = JOptionPane.showConfirmDialog(this, "Cancel " + r.purpose() + " on " + r.date() + " at " + r.start() + "?",
                    "Confirm cancellation", JOptionPane.YES_NO_OPTION);
            if (answer == JOptionPane.YES_OPTION) {
                try { service.cancel(r.id(), student.getText()); refreshMine(); refreshDay(); }
                catch (Exception ex) { error(ex.getMessage()); }
            }
        });
        return panel;
    }

    private void refreshSpaces() {
        Space previous = selectedSpace();
        spaces.clearSelection(); spaceModel.setRowCount(0);
        visibleSpaces = service.spaces((Integer) capacity.getValue());
        for (Space s : visibleSpaces) spaceModel.addRow(new Object[]{s.name(), s.building(), s.capacity()});
        spaceMessage.setText(visibleSpaces.isEmpty() ? "No spaces match this capacity. Clear the filter to see all spaces." : visibleSpaces.size() + " spaces found.");
        int selection = previous == null ? -1 : visibleSpaces.indexOf(previous);
        if (selection >= 0) spaces.setRowSelectionInterval(selection, selection);
        refreshDay();
    }

    private Space selectedSpace() {
        int row = spaces.getSelectedRow(); return row < 0 || row >= visibleSpaces.size() ? null : visibleSpaces.get(row);
    }

    private void refreshDay() {
        Space s = selectedSpace(); scheduleModel.setRowCount(0); create.setEnabled(s != null);
        if (s == null) {
            details.setText("Select a space to view its details."); scheduleMessage.setText("Select a space and a day to view availability.");
        } else {
            details.setText("<html>" + html(s.building()) + " · Capacity: " + s.capacity() + "<br>Features: " + html(String.join(", ", s.features())) + "</html>");
            List<Reservation> reservations = service.forDay(s.id(), selectedDate);
            LocalTime cursor = LocalTime.MIDNIGHT;
            for (Reservation r : reservations) {
                if (r.start().isAfter(cursor)) scheduleModel.addRow(new Object[]{TIME.format(cursor), TIME.format(r.start()), "Available", ""});
                scheduleModel.addRow(new Object[]{TIME.format(r.start()), TIME.format(r.end()), "Reserved", r.purpose()});
                cursor = r.end();
            }
            scheduleModel.addRow(new Object[]{TIME.format(cursor), "24:00", "Available", ""});
            scheduleMessage.setText(selectedDate + " · " + (reservations.isEmpty() ? "Fully available" : reservations.size() + " reservation(s)"));
        }
        refreshCalendar();
    }

    private void refreshCalendar() {
        days.removeAll(); monthLabel.setText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        for (String name : new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}) days.add(new JLabel(name, SwingConstants.CENTER));
        int offset = month.atDay(1).getDayOfWeek().getValue() - 1;
        for (int i = 0; i < offset; i++) days.add(new JLabel());
        Space s = selectedSpace();
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            boolean booked = s != null && !service.forDay(s.id(), date).isEmpty();
            JButton button = new JButton(d + (booked ? " *" : ""));
            button.setToolTipText(date + (booked ? " — has reservations" : ""));
            if (date.equals(selectedDate)) button.setBorder(BorderFactory.createLineBorder(new Color(35, 95, 175), 3));
            button.addActionListener(e -> { selectedDate = date; refreshDay(); }); days.add(button);
        }
        int used = offset + month.lengthOfMonth();
        while (used++ % 7 != 0) days.add(new JLabel());
        days.revalidate(); days.repaint();
    }

    private void refreshMine() {
        mine.clearSelection(); mineModel.setRowCount(0); myReservations = service.forStudent(student.getText());
        for (Reservation r : myReservations) mineModel.addRow(new Object[]{r.date(), TIME.format(r.start()), TIME.format(r.end()), service.space(r.spaceId()).name(), r.purpose()});
        mineMessage.setText(student.getText().isBlank() ? "Enter your student name / ID above." : myReservations.isEmpty() ? "You have no reservations." : myReservations.size() + " reservation(s), sorted by date and start time.");
        refreshButtons();
    }
    private Reservation selectedReservation() {
        int row = mine.getSelectedRow(); return row < 0 || row >= myReservations.size() ? null : myReservations.get(row);
    }
    private void refreshButtons() { boolean selected = selectedReservation() != null; edit.setEnabled(selected); cancel.setEnabled(selected); }

    private void openForm(Reservation existing) {
        if (student.getText().isBlank()) { error("Enter your student name / ID before reserving a space."); return; }
        List<Space> all = service.spaces(0);
        JComboBox<Space> room = new JComboBox<>(all.toArray(Space[]::new));
        room.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value instanceof Space s ? s.name() + " — " + s.building() : "", index, selected, focus);
            }
        });
        room.setSelectedItem(existing == null ? selectedSpace() : service.space(existing.spaceId()));
        JTextField purpose = new JTextField(existing == null ? "" : existing.purpose());
        JTextField date = new JTextField((existing == null ? selectedDate : existing.date()).toString());
        JTextField start = new JTextField(existing == null ? "10:00" : TIME.format(existing.start()));
        JTextField end = new JTextField(existing == null ? "11:00" : TIME.format(existing.end()));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Space:")); form.add(room); form.add(new JLabel("Purpose / activity:")); form.add(purpose);
        form.add(new JLabel("Date (YYYY-MM-DD):")); form.add(date);
        form.add(new JLabel("Start (HH:mm, 24-hour):")); form.add(start); form.add(new JLabel("End (HH:mm, same day):")); form.add(end);
        while (JOptionPane.showConfirmDialog(this, form, existing == null ? "Create reservation" : "Modify reservation", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                if (!start.getText().strip().matches("\\d{2}:\\d{2}") || !end.getText().strip().matches("\\d{2}:\\d{2}"))
                    throw new IllegalArgumentException("Enter times in HH:mm format (00:00–23:59).");
                LocalDate day = LocalDate.parse(date.getText().strip());
                LocalTime from = LocalTime.parse(start.getText().strip()); LocalTime to = LocalTime.parse(end.getText().strip());
                Space chosen = (Space) room.getSelectedItem();
                if (existing == null) service.create(student.getText(), chosen == null ? null : chosen.id(), purpose.getText(), day, from, to);
                else service.update(existing.id(), student.getText(), chosen == null ? null : chosen.id(), purpose.getText(), day, from, to);
                selectedDate = day; month = YearMonth.from(day);
                if (chosen != null && !visibleSpaces.contains(chosen)) capacity.setValue(0);
                int row = visibleSpaces.indexOf(chosen); if (row >= 0) spaces.setRowSelectionInterval(row, row);
                refreshMine(); refreshDay();
                JOptionPane.showMessageDialog(this, "Reservation saved."); return;
            } catch (java.time.format.DateTimeParseException e) { error("Enter a valid date (YYYY-MM-DD) and times (HH:mm, 00:00–23:59)."); }
            catch (Exception e) { error("Could not save reservation: " + e.getMessage()); }
        }
    }
    private void error(String text) { JOptionPane.showMessageDialog(this, text, "Reservation message", JOptionPane.ERROR_MESSAGE); }
    private static String html(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) { public boolean isCellEditable(int row, int column) { return false; } };
    }
}
