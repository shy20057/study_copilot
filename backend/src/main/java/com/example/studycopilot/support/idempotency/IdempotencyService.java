package com.example.studycopilot.support.idempotency;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.security.*;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Durable response replay and mutation share the owner's transaction and lock. */
@Service @RequiredArgsConstructor
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdempotencyProperties properties;
    private final MeterRegistry metrics;

    @Transactional
    public <T> T execute(String operation,String requestId,Object normalizedRequest,Class<T> type,Supplier<T> mutation) {
        long uid=UserContext.requireUserId();
        if (jdbc.queryForList("select id from study_copilot.t_user where id=? and status=1 and deleted=0 for update",uid).isEmpty())
            throw new BusinessException(ScErrorCode.TOKEN_REVOKED);
        if(requestId==null || !requestId.matches("[A-Za-z0-9._:-]{1,100}") || operation==null || operation.length()>50)
            throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        String hash=fingerprint(Map.of("owner",uid,"operation",operation,"requestId",requestId,"request",normalizedRequest));
        var previous=jdbc.query("select request_hash,result::text from study_copilot.t_idempotency_record where user_id=? and operation_type=? and request_id=?",
            (rs,n)->new String[]{rs.getString(1),rs.getString(2)},uid,operation,requestId);
        if(!previous.isEmpty()) {
            if(!hash.equals(previous.get(0)[0])) throw new BusinessException(ScErrorCode.IDEMPOTENCY_CONFLICT);
            try {
                T result = json.readValue(previous.get(0)[1],type);
                metrics.counter("study_copilot.idempotency.replays").increment();
                return result;
            } catch(Exception ex) {throw new IllegalStateException("Invalid durable idempotency response",ex);}
        }
        T result=mutation.get();
        jdbc.update("insert into study_copilot.t_idempotency_record(user_id,operation_type,request_id,request_hash,response_status,result) values (?,?,?,?,200,?::jsonb)",uid,operation,requestId,hash,serialize(result));
        return result;
    }
    public String fingerprint(Object value) {
        try {var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(properties.getHmacSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(serialize(canonical(value)).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex) {throw new IllegalStateException("Cannot fingerprint request",ex);}
    }
    public String serialize(Object value) {try {return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("Cannot encode durable result",ex);}}
    private Object canonical(Object value) {
        if(value instanceof Map<?,?> map) {var sorted=new TreeMap<String,Object>();map.forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted;}
        if(value instanceof Set<?> set) return set.stream().map(this::canonical).sorted(Comparator.comparing(this::serialize)).toList();
        if(value instanceof Collection<?> list) return list.stream().map(this::canonical).toList();
        if(value==null || value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        JsonNode node=json.valueToTree(value);
        if(node.isObject()) {var map=new TreeMap<String,Object>();node.fields().forEachRemaining(e->map.put(e.getKey(),canonicalNode(e.getValue())));return map;}
        return canonicalNode(node);
    }
    private Object canonicalNode(JsonNode node) {
        if(node.isObject()){var map=new TreeMap<String,Object>();node.fields().forEachRemaining(e->map.put(e.getKey(),canonicalNode(e.getValue())));return map;}
        if(node.isArray()){var list=new ArrayList<Object>();node.forEach(v->list.add(canonicalNode(v)));return list;}
        return node;
    }
}
