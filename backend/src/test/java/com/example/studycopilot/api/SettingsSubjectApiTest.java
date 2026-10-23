package com.example.studycopilot.api;

import com.example.studycopilot.domain.time.LearningDayService;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc(print=MockMvcPrint.NONE) @ActiveProfiles("test")
class SettingsSubjectApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired LearningDayService days;
    private final Set<String> names=new HashSet<>();
    @AfterEach void cleanup() {
        for(String name:names) for(long id:jdbc.queryForList("select id from study_copilot.t_user where normalized_username=?",Long.class,name)) {
            jdbc.update("update study_copilot.t_user_setting set default_subject_id=null where user_id=?",id);
            for(String table:List.of("t_focus_session","t_task","t_task_repeat_template","t_refresh_session","t_idempotency_record","t_onboarding_progress","t_pet_status","t_learning_day_rule","t_user_setting","t_subject")) jdbc.update("delete from study_copilot."+table+" where user_id=?",id);
            jdbc.update("delete from study_copilot.t_user where id=?",id);
        }
    }
    @Test void settingsPersistIndependentPreferencesAndRejectStaleVersion() throws Exception {
        var u=user(); var other=user();
        request(get("/api/settings"),u,null).andExpect(jsonPath("$.data.theme").value("SYSTEM"));
        request(put("/api/settings"),u,Map.of("version",0,"theme","SYSTEM","reduceMotion",true,"reduceTransparency",false,"muted",true)).andExpect(status().isOk());
        request(put("/api/settings"),u,Map.of("version",1,"reduceTransparency",true)).andExpect(status().isOk());
        request(get("/api/settings"),u,null).andExpect(jsonPath("$.data.version").value(2)).andExpect(jsonPath("$.data.reduceMotion").value(true)).andExpect(jsonPath("$.data.reduceTransparency").value(true)).andExpect(jsonPath("$.data.theme").value("SYSTEM"));
        request(get("/api/settings"),other,null).andExpect(jsonPath("$.data.reduceMotion").value(false));
        request(put("/api/settings"),u,Map.of("version",0,"theme","DARK")).andExpect(status().isConflict()).andExpect(jsonPath("$.currentVersion").value(2));
        for(var body:List.of(Map.of("version",2,"dayStartHour",7),Map.of("version",2,"timezone","Bogus/Zone"),Map.of("version",2,"theme","bogus"),Map.of("version",2,"weeklyGoalMin",0))) request(put("/api/settings"),u,body).andExpect(status().isBadRequest());
        request(put("/api/settings"),u,Map.of("theme","DARK")).andExpect(status().isBadRequest());
    }
    @Test void ruleChangesAppendHistoryAndResolveAtEffectiveInstant() throws Exception {
        var u=user(); long id=u.path("userId").asLong();
        Instant old=jdbc.queryForObject("select effective_at from study_copilot.t_learning_day_rule where user_id=?",java.sql.Timestamp.class,id).toInstant();
        request(put("/api/settings"),u,Map.of("version",0,"timezone","America/New_York","dayStartHour",4)).andExpect(status().isOk());
        Instant effective=jdbc.queryForObject("select effective_at from study_copilot.t_learning_day_rule where user_id=? and version=2",java.sql.Timestamp.class,id).toInstant();
        assertThat(days.resolve(id,old).ruleVersion()).isEqualTo(1);
        assertThat(days.resolve(id,effective.minusNanos(1000)).ruleVersion()).isEqualTo(1);
        assertThat(days.resolve(id,effective).ruleVersion()).isEqualTo(2);
        request(put("/api/settings"),u,Map.of("version",1,"theme","DARK")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from study_copilot.t_learning_day_rule where user_id=?",Integer.class,id)).isEqualTo(2);
    }
    @Test void activeNamesAreUniqueAndArchiveRestorePreserveHistoryAndVersions() throws Exception {
        var u=user(); var other=user(); long defaultId=subjects(u).get(0).path("id").asLong();
        request(put("/api/subjects/"+defaultId+"/archive"),u,Map.of("version",0)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SC_004004"));
        var s=data(request(post("/api/subjects"),u,Map.of("name"," Math ","color","#123456"))).path("id").asLong();
        request(post("/api/subjects"),u,Map.of("name","mAtH")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SC_004002"));
        request(post("/api/subjects"),other,Map.of("name","Math")).andExpect(status().isOk());
        request(put("/api/subjects/"+s),other,Map.of("name","stolen","version",0)).andExpect(status().isNotFound());
        request(put("/api/subjects/"+s),u,Map.of("name","Math","version",0)).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        request(put("/api/subjects/"+s+"/archive"),u,Map.of("version",0)).andExpect(status().isConflict()).andExpect(jsonPath("$.currentVersion").value(1));
        request(put("/api/subjects/"+s+"/archive"),u,Map.of("version",1)).andExpect(status().isOk());
        request(post("/api/subjects"),u,Map.of("name","Math")).andExpect(status().isOk());
        request(put("/api/subjects/"+s+"/restore"),u,Map.of("version",2)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SC_004002"));
        assertThat(jdbc.queryForObject("select archived from study_copilot.t_subject where user_id=? and id=?",Integer.class,u.path("userId").asLong(),s)).isEqualTo(1);
    }
    @Test void unfinishedTasksRequireExplicitMigrationOrCancellationAndKeepSnapshots() throws Exception {
        var u=user(); long uid=u.path("userId").asLong(), first=subjects(u).get(0).path("id").asLong();
        long target=data(request(post("/api/subjects"),u,Map.of("name","Target"))).path("id").asLong();
        long task=jdbc.queryForObject("insert into study_copilot.t_task(user_id,title,subject_id,subject_name_snapshot) values (?,'pending',?,'Original') returning id",Long.class,uid,first);
        request(put("/api/subjects/"+first+"/archive"),u,Map.of("version",0)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SC_004003"));
        request(put("/api/subjects/"+first+"/archive"),u,Map.of("version",0,"strategy","MIGRATE","targetSubjectId",target)).andExpect(status().isOk());
        var row=jdbc.queryForMap("select subject_id,version,subject_name_snapshot from study_copilot.t_task where id=?",task);
        assertThat(row).containsEntry("subject_id",target).containsEntry("version",1L).containsEntry("subject_name_snapshot","Original");
        request(put("/api/subjects/"+first+"/restore"),u,Map.of("version",1)).andExpect(status().isOk());
        request(put("/api/subjects/"+target+"/archive"),u,Map.of("version",0,"strategy","CANCEL")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from study_copilot.t_task where id=?",String.class,task)).isEqualTo("CANCELLED");
    }
    @Test void runningFocusAndBreakBlockRuleChangesAndSubjectArchive() throws Exception {
        for(String mode:List.of("FOCUS","SHORT","LONG")) {
            var u=user(); long uid=u.path("userId").asLong(), sid=subjects(u).get(0).path("id").asLong();
            request(post("/api/subjects"),u,Map.of("name","Other")).andExpect(status().isOk());
            jdbc.update("insert into study_copilot.t_focus_session(user_id,mode,request_id,subject_id,planned_seconds,start_at,scheduled_end_at,rule_version) values (?,?,?,?,1500,now(),now()+interval '25 minute',1)",uid,mode,UUID.randomUUID().toString(),sid);
            request(put("/api/settings"),u,Map.of("version",0,"dayStartHour",4)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SC_003002"));
            request(put("/api/settings"),u,Map.of("version",0,"reduceMotion",true)).andExpect(status().isOk());
            request(put("/api/subjects/"+sid+"/archive"),u,Map.of("version",0,"strategy","CANCEL")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SC_004003"));
        }
    }
    @Test void databaseRejectsNormalizedActiveDuplicatesButAllowsArchivedDuplicates() throws Exception {
        var u=user(); long uid=u.path("userId").asLong();
        jdbc.update("insert into study_copilot.t_subject(user_id,name) values (?,'Physics')",uid);
        assertThatThrownBy(()->jdbc.update("insert into study_copilot.t_subject(user_id,name) values (?,' PHYSICS ')",uid)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("insert into study_copilot.t_subject(user_id,name,archived) values (?,'Physics',1)",uid);
    }
    @Test void concurrentWritesRetainOneActiveSubjectAndReportTruthfulConflicts() throws Exception {
        var u=user(); long first=subjects(u).get(0).path("id").asLong();
        var createStatuses=concurrent(
            ()->request(post("/api/subjects"),u,Map.of("name","Concurrent")).andReturn().getResponse().getStatus(),
            ()->request(post("/api/subjects"),u,Map.of("name"," CONCURRENT ")).andReturn().getResponse().getStatus());
        assertThat(createStatuses).containsExactlyInAnyOrder(200,409);
        long second=subjects(u).get(1).path("id").asLong();
        var archiveStatuses=concurrent(
            ()->request(put("/api/subjects/"+first+"/archive"),u,Map.of("version",0)).andReturn().getResponse().getStatus(),
            ()->request(put("/api/subjects/"+second+"/archive"),u,Map.of("version",0)).andReturn().getResponse().getStatus());
        assertThat(archiveStatuses).containsExactlyInAnyOrder(200,409);
        assertThat(jdbc.queryForObject("select count(*) from study_copilot.t_subject where user_id=? and archived=0",Integer.class,u.path("userId").asLong())).isEqualTo(1);
    }
    @Test void simultaneousSettingsUpdatesHaveOneWinnerAndOneCurrentVersionConflict() throws Exception {
        var u=user();
        var results=concurrent(
            ()->request(put("/api/settings"),u,Map.of("version",0,"theme","DARK")).andReturn().getResponse().getStatus(),
            ()->request(put("/api/settings"),u,Map.of("version",0,"theme","LIGHT")).andReturn().getResponse().getStatus());
        assertThat(results).containsExactlyInAnyOrder(200,409);
        request(get("/api/settings"),u,null).andExpect(jsonPath("$.data.version").value(1));
    }
    @Test void archiveMigrationRejectsForeignTargetsAndUpdatesDefaultWithoutChangingHistory() throws Exception {
        var u=user(); var other=user(); long uid=u.path("userId").asLong(), first=subjects(u).get(0).path("id").asLong();
        long foreign=subjects(other).get(0).path("id").asLong();
        long target=data(request(post("/api/subjects"),u,Map.of("name","Keep"))).path("id").asLong();
        jdbc.update("insert into study_copilot.t_task(user_id,title,subject_id,status,subject_name_snapshot) values (?,'historical',?,'DONE','Before rename')",uid,first);
        jdbc.update("insert into study_copilot.t_task_repeat_template(user_id,title,subject_id,repeat_rule,start_date) values (?,'repeat',?,'DAILY',current_date)",uid,first);
        request(put("/api/subjects/"+first+"/archive"),u,Map.of("version",0,"strategy","MIGRATE","targetSubjectId",foreign)).andExpect(status().isNotFound());
        request(put("/api/settings"),u,Map.of("version",0,"defaultSubjectId",foreign)).andExpect(status().isNotFound());
        request(put("/api/subjects/"+first+"/archive"),u,Map.of("version",0)).andExpect(status().isConflict());
        request(put("/api/subjects/"+first+"/archive"),u,Map.of("version",0,"strategy","MIGRATE","targetSubjectId",target)).andExpect(status().isOk()).andExpect(jsonPath("$.data.archived").value(1)).andExpect(jsonPath("$.data.version").value(1));
        request(get("/api/settings"),u,null).andExpect(jsonPath("$.data.defaultSubjectId").value(target)).andExpect(jsonPath("$.data.version").value(1));
        assertThat(jdbc.queryForObject("select subject_id from study_copilot.t_task_repeat_template where user_id=?",Long.class,uid)).isEqualTo(target);
        var historical=jdbc.queryForMap("select subject_id,subject_name_snapshot,version from study_copilot.t_task where user_id=?",uid);
        assertThat(historical).containsEntry("subject_id",first).containsEntry("subject_name_snapshot","Before rename").containsEntry("version",0L);
    }
    @Test void weeklyGoalCanBeClearedAndNonRulePreferencesDoNotCreateRules() throws Exception {
        var u=user();
        request(put("/api/settings"),u,Map.of("version",0,"weeklyGoalMin",120)).andExpect(status().isOk());
        var clear=new HashMap<String,Object>(); clear.put("version",1); clear.put("weeklyGoalMin",null);
        request(put("/api/settings"),u,clear).andExpect(status().isOk());
        request(get("/api/settings"),u,null).andExpect(jsonPath("$.data.weeklyGoalMin").isEmpty()).andExpect(jsonPath("$.data.ruleVersion").value(1));
    }
    private <T> List<T> concurrent(java.util.concurrent.Callable<T> first,java.util.concurrent.Callable<T> second) throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready=new java.util.concurrent.CountDownLatch(2); var start=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<T> a=()->{ready.countDown();start.await();return first.call();};
            java.util.concurrent.Callable<T> b=()->{ready.countDown();start.await();return second.call();};
            var one=pool.submit(a); var two=pool.submit(b);
            assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(one.get(30,java.util.concurrent.TimeUnit.SECONDS),two.get(30,java.util.concurrent.TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
    }
    private JsonNode user() throws Exception {String name="settings_t4_"+UUID.randomUUID().toString().replace("-","").substring(0,20); names.add(name); return data(mvc.perform(post("/api/auth/register").header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"password","Study-pass_123")))));}
    private JsonNode subjects(JsonNode u) throws Exception {return data(request(get("/api/subjects"),u,null));}
    private ResultActions request(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,JsonNode u,Object body) throws Exception {req.header("Authorization","Bearer "+u.path("accessToken").asText()); if(body!=null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)); return mvc.perform(req);}
    private JsonNode data(ResultActions response) throws Exception {response.andExpect(status().isOk());return json.readTree(response.andReturn().getResponse().getContentAsString()).path("data");}
}
