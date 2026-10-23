package com.example.studycopilot.domain.time;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class LearningDayServiceTest {
    private final LearningDayService service = new LearningDayService(null);
    private final TimeSliceService slicer = new TimeSliceService(service);

    @Test void threeAmBelongsToPreviousLearningDayWhenBoundaryIsFour() {
        var result = service.resolve(Instant.parse("2026-10-22T19:00:00Z"), ZoneId.of("Asia/Shanghai"), 4, 7);
        assertThat(result.learningDate()).isEqualTo(LocalDate.of(2026,10,22));
        assertThat(result.ruleVersion()).isEqualTo(7);
        assertThat(result.start()).isEqualTo(Instant.parse("2026-10-21T20:00:00Z"));
        assertThat(result.end()).isEqualTo(Instant.parse("2026-10-22T20:00:00Z"));
    }
    @Test void intervalAcrossFourAmSplitsIntoTwoHalfOpenSlices() {
        var slices = slicer.split(Instant.parse("2026-10-22T19:50:00Z"),Instant.parse("2026-10-22T20:15:00Z"),ZoneId.of("Asia/Shanghai"),4,7);
        assertThat(slices).extracting(LearningSlice::seconds).containsExactly(600L,900L);
        assertThat(slices).extracting(LearningSlice::learningDate).containsExactly(LocalDate.of(2026,10,22),LocalDate.of(2026,10,23));
        assertThat(slices.get(0).end()).isEqualTo(slices.get(1).start());
        assertThat(slices).extracting(LearningSlice::ruleVersion).containsOnly(7L);
    }
    @Test void boundaryIsHalfOpenAndEmptyIntervalHasNoSlices() {
        var boundary=Instant.parse("2026-10-22T20:00:00Z"); var zone=ZoneId.of("Asia/Shanghai");
        assertThat(service.resolve(boundary,zone,4,7).learningDate()).isEqualTo(LocalDate.of(2026,10,23));
        assertThat(slicer.split(boundary.minusSeconds(60),boundary,zone,4,7)).hasSize(1);
        assertThat(slicer.split(boundary,boundary,zone,4,7)).isEmpty();
        assertThatThrownBy(()->slicer.split(boundary,boundary.minusSeconds(1),zone,4,7)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void springGapUsesActualResolvedBoundaryAndTwentyThreeHourDay() {
        var zone=ZoneId.of("America/New_York");
        var before=service.resolve(Instant.parse("2026-03-08T06:59:59Z"),zone,2,1);
        var after=service.resolve(Instant.parse("2026-03-08T07:00:00Z"),zone,2,1);
        assertThat(before.learningDate()).isEqualTo(LocalDate.of(2026,3,7));
        assertThat(after.learningDate()).isEqualTo(LocalDate.of(2026,3,8));
        assertThat(after.start()).isEqualTo(Instant.parse("2026-03-08T07:00:00Z"));
        assertThat(Duration.between(after.start(),after.end()).toHours()).isEqualTo(23);
    }
    @Test void fallOverlapChoosesEarlierBoundaryAndDoesNotMoveBackOnSecondOccurrence() {
        var zone=ZoneId.of("America/New_York");
        var result=service.resolve(Instant.parse("2026-11-01T06:15:00Z"),zone,1,1);
        assertThat(result.start()).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
        assertThat(Duration.between(result.start(),result.end()).toHours()).isEqualTo(25);
        assertThat(slicer.split(Instant.parse("2026-11-01T04:30:00Z"),Instant.parse("2026-11-01T07:00:00Z"),zone,1,1))
            .extracting(LearningSlice::seconds).containsExactly(1800L,7200L);
    }
    @Test void invalidRulesAreRejected() {
        for(int hour: new int[]{-1,7}) assertThatThrownBy(()->service.resolve(Instant.EPOCH,ZoneId.of("UTC"),hour,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.resolve(Instant.EPOCH,ZoneId.of("UTC"),0,0)).isInstanceOf(IllegalArgumentException.class);
    }
}
