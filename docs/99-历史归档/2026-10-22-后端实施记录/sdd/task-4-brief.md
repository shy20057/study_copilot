### Task 4: Settings, Subjects, and Learning-Day Rules

> 历史归档：本文记录早期方案或实施过程，其中的流程要求、授权和验证结论仅适用于当时。当前状态见 [后端交付说明](../../../04-交付验收/一期A后端交付说明.md)，旧路径的新位置见 [归档索引](../../README.md)。

**Files:**
- Modify: `backend/src/main/java/com/example/studycopilot/service/setting/**`
- Modify: `backend/src/main/java/com/example/studycopilot/controller/setting/SettingController.java`
- Modify: `backend/src/main/java/com/example/studycopilot/service/subject/**`
- Modify: `backend/src/main/java/com/example/studycopilot/controller/subject/SubjectController.java`
- Modify: `backend/src/main/java/com/example/studycopilot/dao/entity/setting/UserSettingDO.java`
- Modify: `backend/src/main/java/com/example/studycopilot/dao/entity/subject/SubjectDO.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/entity/setting/LearningDayRuleDO.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/mapper/setting/LearningDayRuleMapper.java`
- Create: `backend/src/main/java/com/example/studycopilot/domain/time/LearningDayService.java`
- Create: `backend/src/main/java/com/example/studycopilot/domain/time/TimeSliceService.java`
- Create: `backend/src/test/java/com/example/studycopilot/domain/time/LearningDayServiceTest.java`
- Create: `backend/src/test/java/com/example/studycopilot/api/SettingsSubjectApiTest.java`

**Interfaces:**
- Produces: `LearningDayContext resolve(long userId, Instant instant)` for persisted user rules.
- Produces: `LearningDayContext resolve(Instant instant, ZoneId zone, int dayStartHour, long ruleVersion)` for deterministic domain calculation.
- Produces: `List<LearningSlice> split(Instant start, Instant end, ZoneId zone, int dayStartHour, long ruleVersion)`.

- [ ] **Step 1: Write learning-day unit tests**

```java
@Test
void threeAmBelongsToPreviousLearningDayWhenBoundaryIsFour() {
    LearningDayContext result = service.resolve(
        Instant.parse("2026-10-22T19:00:00Z"), ZoneId.of("Asia/Shanghai"), 4, 7L);
    assertThat(result.learningDate()).isEqualTo(LocalDate.of(2026, 10, 22));
    assertThat(result.ruleVersion()).isEqualTo(7L);
}

@Test
void intervalAcrossFourAmSplitsIntoTwoHalfOpenSlices() {
    var slices = slicer.split(
        Instant.parse("2026-10-22T19:50:00Z"),
        Instant.parse("2026-10-22T20:15:00Z"),
        ZoneId.of("Asia/Shanghai"), 4, 7L);
    assertThat(slices).extracting(LearningSlice::seconds).containsExactly(600L, 900L);
}
```

- [ ] **Step 2: Run time tests and confirm failure**

```powershell
mvn -f backend/pom.xml -Dtest=LearningDayServiceTest test
```

- [ ] **Step 3: Implement time rules and slicing**

Use half-open intervals `[start,end)`. A rule change creates a new version with `effective_at`; it never rewrites previous slices. Reject timezone/day-start changes while a session is running.

- [ ] **Step 4: Write and run subject/settings API tests**

Test at least one active subject, case-insensitive active-name uniqueness, version conflicts, final-subject archive rejection, and persisted `SYSTEM`/motion/transparency preferences.

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=SettingsSubjectApiTest test
```

Expected before implementation: FAIL.

- [ ] **Step 5: Implement settings and subject services**

All queries filter by user. Archive never deletes history. If unfinished tasks exist, require an explicit migration/cancel strategy. Persist transparency and reduced-motion independently.

- [ ] **Step 6: Run both test classes**

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=LearningDayServiceTest,SettingsSubjectApiTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/example/studycopilot/domain/time backend/src/main/java/com/example/studycopilot/controller/setting backend/src/main/java/com/example/studycopilot/controller/subject backend/src/main/java/com/example/studycopilot/service/setting backend/src/main/java/com/example/studycopilot/service/subject backend/src/main/java/com/example/studycopilot/dao backend/src/main/java/com/example/studycopilot/dto backend/src/test
git commit -m "feat: add settings subjects and learning day rules"
```

---

