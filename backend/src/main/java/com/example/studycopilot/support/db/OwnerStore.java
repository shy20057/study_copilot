package com.example.studycopilot.support.db;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.security.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component @RequiredArgsConstructor
public class OwnerStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public long owner(){return UserContext.requireUserId();}

    // Shared handle for callers that need queries beyond the helpers below.
    public JdbcTemplate jdbc(){return jdbc;}

    // Locks the owning user row. Every cross-resource mutation must call this
    // before touching settings, subjects, tasks, templates or sessions.
    public void lock(long uid) {
        if(jdbc.queryForList("select id from study_copilot.t_user where id=? and deleted=0 and status=1 for update",uid).isEmpty())
            throw error(ScErrorCode.TOKEN_REVOKED);
    }

    public Map<String,Object> one(String sql,Object...args) {
        var rows=jdbc.queryForList(sql,args);
        if(rows.isEmpty())throw error(ScErrorCode.NOT_FOUND);
        return rows.get(0);
    }

    public Map<String,Object> task(long uid,long id){return one("select * from study_copilot.t_task where user_id=? and id=?",uid,id);}

    public Map<String,Object> subject(long uid,long id){return one("select * from study_copilot.t_subject where user_id=? and id=? and archived=0",uid,id);}

    public void version(Map<String,Object> row,Object expected) {
        if(expected==null)throw error(ScErrorCode.VALIDATION_ERROR);
        if(number(row.get("version"))!=number(expected))throw new BusinessException(ScErrorCode.VERSION_CONFLICT,number(row.get("version")),null);
    }

    public String encode(Object value) {
        try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Invalid JSON");}
    }

    @SuppressWarnings("unchecked") public Map<String,Object> decode(Object value) {
        if (value instanceof Map<?,?> map) return new LinkedHashMap<>((Map<String,Object>) map);
        try{return json.readValue(value.toString(),Map.class);}catch(Exception e){throw new IllegalStateException("Invalid stored JSON");}
    }

    public static long number(Object value) {
        try{return Long.parseLong(value.toString());}catch(Exception e){throw error(ScErrorCode.VALIDATION_ERROR);}
    }

    public static int integer(Map<String,Object> m,String k,int fallback) {
        try {
            return m.get(k)==null?fallback:Math.toIntExact(number(m.get(k)));
        } catch (ArithmeticException ex) {
            throw error(ScErrorCode.VALIDATION_ERROR);
        }
    }

    public static String text(Map<String,Object> m,String k,String fallback){return m.get(k)==null?fallback:m.get(k).toString();}

    public static LocalDate date(Object v) {
        try {
            return v==null?null:LocalDate.parse(v.toString());
        } catch (DateTimeException ex) {
            throw error(ScErrorCode.VALIDATION_ERROR);
        }
    }

    public static Instant instant(Object v) {
        if (v == null) throw error(ScErrorCode.VALIDATION_ERROR);
        try {
            if(v instanceof java.sql.Timestamp t)return t.toInstant();
            if(v instanceof OffsetDateTime t)return t.toInstant();
            return Instant.parse(v.toString());
        } catch (DateTimeException ex) {
            throw error(ScErrorCode.VALIDATION_ERROR);
        }
    }

    public static BusinessException error(ScErrorCode c){return new BusinessException(c);}
}
