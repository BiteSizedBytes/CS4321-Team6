package model;
import java.util.List;

public record Space(String id, String name, String building, int capacity, List<String> features) {
    public Space { features = List.copyOf(features); }
    public static List<Space> samples() {
        return List.of(
            new Space("library-101", "Study Room 101", "Library", 6, List.of("Whiteboard", "Power outlets")),
            new Space("library-202", "Group Study 202", "Library", 12, List.of("Display screen", "Whiteboard")),
            new Space("science-110", "Seminar Room 110", "Science Center", 30, List.of("Projector", "Accessible entrance")),
            new Space("union-hall", "Activity Hall", "Student Union", 80, List.of("Movable seating", "Sound system")));
    }
}
