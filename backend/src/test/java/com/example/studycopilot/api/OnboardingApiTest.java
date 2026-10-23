package com.example.studycopilot.api;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc(print=MockMvcPrint.NONE) @ActiveProfiles("test")
class OnboardingApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    private final Set<String> names=new HashSet<>();
    @AfterEach void cleanup() {
        for(String name:names) for(long uid:jdbc.queryForList("select id from study_copilot.t_user where normalized_username=?",Long.class,name)) {
            jdbc.update("update study_copilot.t_user_setting set default_subject_id=null where user_id=?",uid);
            for(String table:List.of("t_task","t_template_import_batch","t_refresh_session","t_idempotency_record","t_onboarding_progress","t_pet_status","t_learning_day_rule","t_user_setting","t_subject")) jdbc.update("delete from study_copilot."+table+" where user_id=?",uid);
            jdbc.update("delete from study_copilot.t_user where id=?",uid);
        }
    }
    @Test void progressRestoresConfirmedDataSkipsAndResumesWithoutLosingDefaults() throws Exception {
        var u=user(); var other=user();
        call(put("/api/onboarding/progress"),u,Map.of("version",0,"currentStep","SUBJECTS","confirmedData",Map.of("petType",2,"goalName","Exam"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        call(get("/api/onboarding/progress"),u,null).andExpect(jsonPath("$.data.confirmedData.petType").value(2)).andExpect(jsonPath("$.data.currentStep").value("SUBJECTS"));
        call(get("/api/onboarding/progress"),other,null).andExpect(jsonPath("$.data.version").value(0));
        call(put("/api/onboarding/progress"),u,Map.of("version",0,"currentStep","WELCOME","confirmedData",Map.of())).andExpect(status().isConflict()).andExpect(jsonPath("$.currentVersion").value(1));
        complete(u,"skip",Map.of("version",1,"skip",true)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SKIPPED"));
        assertThat(count(u,"t_subject")).isEqualTo(1); assertThat(count(u,"t_pet_status")).isEqualTo(1);
        call(put("/api/onboarding/progress"),u,Map.of("version",2,"currentStep","TEMPLATE","confirmedData",Map.of("goalName","Exam"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        call(get("/api/onboarding/progress"),u,null).andExpect(jsonPath("$.data.confirmedData.petType").value(2));
    }
    @Test void previewsAreReadOnlyVersionedBoundedAndRespectAvailableTime() throws Exception {
        var u=user();
        var list=data(call(get("/api/templates"),u,null)); assertThat(list.size()).isEqualTo(4);
        var p=preview(u); assertThat(p.path("previewVersion").asText()).isNotBlank();
        assertThat(p.path("items").size()).isGreaterThan(3);
        var dates=new HashSet<String>(); int today=0;
        for(var i:p.path("items")) {dates.add(i.path("learningDate").asText()); if(i.path("learningDate").equals(p.path("learningDate"))&&i.path("selectedByDefault").asBoolean()) today++;}
        assertThat(dates.size()).isLessThanOrEqualTo(7); assertThat(today).isLessThanOrEqualTo(3);
        assertThat(count(u,"t_task")).isZero(); assertThat(count(u,"t_template_import_batch")).isZero();
        long uid=u.path("userId").asLong(); jdbc.update("update study_copilot.t_user_setting set daily_avail_min=50 where user_id=?",uid);
        var limited=preview(u); int minutes=0; for(var i:limited.path("items")) if(i.path("learningDate").equals(limited.path("learningDate"))&&i.path("selectedByDefault").asBoolean()) minutes+=i.path("estimatedFocusMinutes").asInt();
        assertThat(minutes).isLessThanOrEqualTo(35); assertThat(limited.path("previewVersion")).isNotEqualTo(p.path("previewVersion"));
        call(get("/api/templates/foundation/preview").param("itemIds","missing"),u,null).andExpect(status().isBadRequest());
        call(get("/api/templates/missing/preview"),u,null).andExpect(status().isNotFound());
    }
    @Test void selectedImportReplaysAfterLostResponseAndConflictsOnPayloadChange() throws Exception {
        var u=user(); var p=preview(u); var body=body(p,0,List.of("d0-read","d1-review"));
        var result=data(complete(u,"lost-response",body));
        var replay=data(complete(u,"lost-response",body)); assertThat(replay).isEqualTo(result);
        assertThat(count(u,"t_task")).isEqualTo(2); assertThat(count(u,"t_template_import_batch")).isEqualTo(1);
        assertThat(result.path("taskIds").size()).isEqualTo(2);
        var changed=new HashMap<>(body); changed.put("itemIds",List.of("d0-read"));
        complete(u,"lost-response",changed).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SC_001006"));
        call(get("/api/onboarding/progress"),u,null).andExpect(jsonPath("$.data.status").value("COMPLETED")).andExpect(jsonPath("$.data.version").value(1));
    }
    @Test void semanticSetAndMapOrderReplayAndOwnerScopeIsIndependent() throws Exception {
        var u=user(); var p=preview(u); var b=body(p,0,List.of("d0-read","d1-review"));
        b.put("overrides",Map.of("d0-read",Map.of("title"," Custom ","plannedPomodoros",2)));
        var result=data(complete(u,"ordered",b));
        var reordered=body(p,0,List.of("d1-review","d0-read")); reordered.put("overrides",Map.of("d0-read",Map.of("plannedPomodoros",2,"title","Custom")));
        assertThat(data(complete(u,"ordered",reordered))).isEqualTo(result);
        var other=user(); data(complete(other,"ordered",body(preview(other),0,List.of("d0-read"))));
        assertThat(count(other,"t_task")).isEqualTo(1);
    }
    @Test void stalePreviewAndUnselectedOverridesAreRejectedWithoutWrites() throws Exception {
        var u=user(); var p=preview(u); long uid=u.path("userId").asLong();
        jdbc.update("update study_copilot.t_user_setting set focus_minutes=30,version=version+1 where user_id=?",uid);
        complete(u,"stale",body(p,0,List.of("d0-read"))).andExpect(status().isConflict());
        assertThat(count(u,"t_task")).isZero(); assertThat(count(u,"t_template_import_batch")).isZero();
        var b=body(preview(u),0,List.of("d0-read")); b.put("overrides",Map.of("d1-review",Map.of("title","unexpected")));
        complete(u,"unselected",b).andExpect(status().isBadRequest());
    }
    @Test void sameTitleManualTaskAndImportedHistoryRemainDistinctAfterReschedulingAndDeletion() throws Exception {
        var u=user(); var p=preview(u); long uid=u.path("userId").asLong();
        String title=p.path("items").get(0).path("title").asText();
        jdbc.update("insert into study_copilot.t_task(user_id,title) values (?,?)",uid,title);
        data(complete(u,"first",body(p,0,List.of("d0-read"))));
        jdbc.update("update study_copilot.t_task set deleted=1,learning_date=learning_date+1,target_date=target_date+1 where user_id=? and import_batch_id is not null",uid);
        var again=preview(u); assertThat(again.path("items").get(0).path("alreadyImported").asBoolean()).isTrue();
        var result=data(complete(u,"second",body(again,1,List.of("d0-read"))));
        assertThat(result.path("taskIds").size()).isZero(); assertThat(result.path("skippedItemIds").get(0).asText()).isEqualTo("d0-read"); assertThat(count(u,"t_task")).isEqualTo(2);
    }
    @Test void invalidLaterItemRollsBackWholeImportAndSameKeyCanRetry() throws Exception {
        var u=user(); var foreign=user(); var p=preview(u);
        long sid=jdbc.queryForObject("select id from study_copilot.t_subject where user_id=?",Long.class,foreign.path("userId").asLong());
        var b=body(p,0,List.of("d0-read","d1-review")); b.put("overrides",Map.of("d1-review",Map.of("subjectId",sid,"subjectVersion",0)));
        complete(u,"rollback",b).andExpect(status().isNotFound());
        assertThat(count(u,"t_task")).isZero(); assertThat(count(u,"t_template_import_batch")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from study_copilot.t_idempotency_record where user_id=? and operation_type='ONBOARDING_COMPLETE'",Integer.class,u.path("userId").asLong())).isZero();
        call(get("/api/onboarding/progress"),u,null).andExpect(jsonPath("$.data.version").value(0)).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        b.remove("overrides"); data(complete(u,"rollback",b)); assertThat(count(u,"t_task")).isEqualTo(2);
    }
    @Test void activeOwnedSubjectAndVersionAreRequiredForOverrides() throws Exception {
        var u=user(); var p=preview(u); long uid=u.path("userId").asLong();
        long sid=jdbc.queryForObject("insert into study_copilot.t_subject(user_id,name,archived,version) values (?,'Old',1,3) returning id",Long.class,uid);
        var b=body(p,0,List.of("d0-read")); b.put("overrides",Map.of("d0-read",Map.of("subjectId",sid,"subjectVersion",3)));
        complete(u,"archived",b).andExpect(status().isConflict());
        jdbc.update("update study_copilot.t_subject set archived=0 where id=?",sid);
        b.put("previewVersion",preview(u).path("previewVersion").asText());
        b.put("overrides",Map.of("d0-read",Map.of("subjectId",sid,"subjectVersion",2)));
        complete(u,"version",b).andExpect(status().isConflict()).andExpect(jsonPath("$.currentVersion").value(3));
    }
    @Test void simultaneousSameKeyReturnsSingleStableBatch() throws Exception {
        var u=user(); var b=body(preview(u),0,List.of("d0-read")); var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try { Callable<JsonNode> c=()->{start.await();return data(complete(u,"concurrent",b));}; var a=pool.submit(c); var d=pool.submit(c); start.countDown(); assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(d.get(30,TimeUnit.SECONDS)); }
        finally {pool.shutdownNow();}
        assertThat(count(u,"t_task")).isEqualTo(1); assertThat(count(u,"t_template_import_batch")).isEqualTo(1);
    }
    @Test void completionWithoutTemplateAndMissingAuthenticationHaveExplicitContracts() throws Exception {
        var u=user(); data(complete(u,"self-arrange",Map.of("version",0,"skip",false)));
        assertThat(count(u,"t_task")).isZero(); assertThat(count(u,"t_template_import_batch")).isZero();
        mvc.perform(get("/api/onboarding/progress")).andExpect(status().isUnauthorized()); mvc.perform(get("/api/templates")).andExpect(status().isUnauthorized());
        call(post("/api/onboarding/complete"),u,Map.of("version",1,"skip",true)).andExpect(status().isBadRequest());
    }
    @Test void insufficientTargetDateExplainsShorterTemplateAndNeverCompressesStages() throws Exception {
        var u=user(); var p=preview(u); String date=p.path("learningDate").asText();
        jdbc.update("update study_copilot.t_user_setting set exam_date=?::date+2 where user_id=?",date,u.path("userId").asLong());
        call(get("/api/templates/foundation/preview"),u,null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SC_015001"));
        var sprint=data(call(get("/api/templates/sprint/preview"),u,null));
        assertThat(sprint.path("items").size()).isEqualTo(6);
        assertThat(sprint.path("stages").get(0).path("endDate").asText()).isEqualTo(java.time.LocalDate.parse(date).plusDays(2).toString());
        assertThat(count(u,"t_task")).isZero();
    }
    @Test void outOfWindowSelectedItemIsRejectedAndExplicitTodaySelectionMayExceedDefaultCap() throws Exception {
        var u=user();var p=preview(u);String finalDate=java.time.LocalDate.parse(p.path("learningDate").asText()).plusDays(6).toString();
        call(get("/api/templates/foundation/preview").param("learningDate",finalDate).param("itemIds","d1-review"),u,null).andExpect(status().isBadRequest());
        data(complete(u,"explicit-today",body(p,0,List.of("d0-read","d0-practice","d0-recall","d0-extra"))));
        assertThat(count(u,"t_task")).isEqualTo(4);
    }
    @Test void databaseIdentityRejectsDuplicatesAcrossResourceVersionsAndPreservesPomodoroEstimate() throws Exception {
        var u=user();var p=preview(u);data(complete(u,"identity",body(p,0,List.of("d0-read"))));long uid=u.path("userId").asLong();
        jdbc.update("update study_copilot.t_user_setting set focus_minutes=50 where user_id=?",uid);
        assertThat(jdbc.queryForObject("select estimated_focus_minutes from study_copilot.t_task where user_id=?",Integer.class,uid)).isEqualTo(25);
        assertThatThrownBy(()->jdbc.update("insert into study_copilot.t_task(user_id,title,import_batch_id,template_id,template_version,template_item_id,template_learning_date) select user_id,'different title',import_batch_id,template_id,'2',template_item_id,template_learning_date from study_copilot.t_task where user_id=?",uid)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void defaultPreviewTotalsDescribeSuggestedNewTasks() throws Exception {
        var u=user();
        call(get("/api/templates/foundation/preview"),u,null).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.taskCount").value(9)).andExpect(jsonPath("$.data.todayTaskCount").value(3))
            .andExpect(jsonPath("$.data.estimatedFocusMinutes").value(275));
    }
    @Test void explicitSingleSelectionHasSingleTaskTotals() throws Exception {
        var u=user();
        call(get("/api/templates/foundation/preview").param("itemIds","d0-read"),u,null).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.taskCount").value(1)).andExpect(jsonPath("$.data.todayTaskCount").value(1))
            .andExpect(jsonPath("$.data.estimatedFocusMinutes").value(25));
    }
    @Test void alreadyImportedSelectionContributesNothingToPreviewTotals() throws Exception {
        var u=user();data(complete(u,"summary-import",body(preview(u),0,List.of("d0-read"))));
        call(get("/api/templates/foundation/preview").param("itemIds","d0-read"),u,null).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.taskCount").value(0)).andExpect(jsonPath("$.data.todayTaskCount").value(0))
            .andExpect(jsonPath("$.data.estimatedFocusMinutes").value(0))
            .andExpect(jsonPath("$.data.items[0].alreadyImported").value(true));
    }
    @Test void noTargetSprintPreviewAndImportedDatesStayWithinThreeDayStage() throws Exception {
        var u=user();var p=data(call(get("/api/templates/sprint/preview"),u,null));
        assertThat(p.path("items").size()).isEqualTo(6);
        String end=java.time.LocalDate.parse(p.path("learningDate").asText()).plusDays(2).toString();
        assertThat(p.path("stages").get(0).path("endDate").asText()).isEqualTo(end);
        for(var item:p.path("items"))assertThat(item.path("learningDate").asText()).isLessThanOrEqualTo(end);
        var b=body(p,0,List.of("d0-read","d1-review","d2-practice"));b.put("templateId","sprint");
        data(complete(u,"sprint-valid",b));
        assertThat(count(u,"t_task")).isEqualTo(3);
        assertThat(jdbc.queryForObject("select max(template_learning_date)::text from study_copilot.t_task where user_id=?",String.class,u.path("userId").asLong())).isEqualTo(end);
    }
    @Test void noTargetSprintRejectsCompletionBeyondItsStageWithoutWrites() throws Exception {
        var u=user();var p=data(call(get("/api/templates/sprint/preview"),u,null));
        var b=body(p,0,List.of("d3-read"));b.put("templateId","sprint");
        complete(u,"sprint-outside",b).andExpect(status().isBadRequest());
        assertThat(count(u,"t_task")).isZero();assertThat(count(u,"t_template_import_batch")).isZero();
        call(get("/api/templates/sprint/preview").param("itemIds","d3-read"),u,null).andExpect(status().isBadRequest());
    }
    private long count(JsonNode u,String table) {return jdbc.queryForObject("select count(*) from study_copilot."+table+" where user_id=?",Long.class,u.path("userId").asLong());}
    private Map<String,Object> body(JsonNode p,long version,List<String> ids) {var b=new HashMap<String,Object>(); b.put("version",version);b.put("templateId","foundation");b.put("previewVersion",p.path("previewVersion").asText());b.put("learningDate",p.path("learningDate").asText());b.put("itemIds",ids);return b;}
    private JsonNode preview(JsonNode u) throws Exception {return data(call(get("/api/templates/foundation/preview"),u,null));}
    private ResultActions complete(JsonNode u,String key,Object body) throws Exception {return call(post("/api/onboarding/complete").header("Idempotency-Key",key),u,body);}
    private JsonNode user() throws Exception {String name="onboard_t5_"+UUID.randomUUID().toString().replace("-","").substring(0,20); names.add(name); return data(mvc.perform(post("/api/auth/register").header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"password","Study-pass_123")))));}
    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,JsonNode u,Object body) throws Exception {req.header("Authorization","Bearer "+u.path("accessToken").asText());if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));return mvc.perform(req);}
    private JsonNode data(ResultActions r) throws Exception {r.andExpect(status().isOk());return json.readTree(r.andReturn().getResponse().getContentAsString()).path("data");}
}
