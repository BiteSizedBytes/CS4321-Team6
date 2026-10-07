package model;

import java.time.LocalDateTime;

public record ScheduleBlock(
    LocalDateTime start,
    LocalDateTime end,
    boolean reserved,
    String purpose
) {

}
