package com.example.studycopilot.domain.time;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class TimeSliceService {
    private final LearningDayService days;
    public List<LearningSlice> split(Instant start, Instant end, ZoneId zone, int dayStartHour,long ruleVersion) {
        Objects.requireNonNull(start); Objects.requireNonNull(end);
        if(end.isBefore(start)) throw new IllegalArgumentException("End precedes start");
        var context=days.resolve(start,zone,dayStartHour,ruleVersion);
        var slices=new ArrayList<LearningSlice>();
        for(Instant cursor=start;cursor.isBefore(end);) {
            Instant boundary=context.end().isBefore(end)?context.end():end;
            if(!boundary.isAfter(cursor)) throw new IllegalArgumentException("Non-increasing learning-day boundary");
            slices.add(new LearningSlice(context.learningDate(),ruleVersion,cursor,boundary));
            cursor=boundary;
            if(cursor.isBefore(end)) context=days.resolve(cursor,zone,dayStartHour,ruleVersion);
        }
        return List.copyOf(slices);
    }
}
