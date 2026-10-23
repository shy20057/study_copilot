package com.example.studycopilot.domain.time;
import java.time.*;
public record LearningSlice(LocalDate learningDate, long ruleVersion, Instant start, Instant end) {
    public long seconds() { return Duration.between(start,end).getSeconds(); }
}
