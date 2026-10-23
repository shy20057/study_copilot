package com.example.studycopilot.domain.time;
import java.time.*;
/** Boundaries describe [start,end); version identifies the immutable adopted rule. */
public record LearningDayContext(LocalDate learningDate, ZoneId zone, int dayStartHour,
                                 long ruleVersion, Instant start, Instant end) {}
