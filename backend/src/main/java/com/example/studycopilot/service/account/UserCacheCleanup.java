package com.example.studycopilot.service.account;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 只清理当前项目、当前用户的缓存命名空间，禁止全库清理。 */
@Component
@RequiredArgsConstructor
public class UserCacheCleanup {
    private final StringRedisTemplate redis;

    @Value("${study-copilot.redis-key-prefix:study-copilot:}")
    private String keyPrefix;

    public void removeOwner(long userId) {
        // Escape Redis glob metacharacters in a configured prefix before appending our wildcard.
        String ownerPrefix = keyPrefix + "user:" + userId + ":";
        String pattern = ownerPrefix.replace("\\", "\\\\").replace("*", "\\*")
                .replace("?", "\\?").replace("[", "\\[").replace("]", "\\]") + "*";
        redis.execute((RedisCallback<Void>) connection -> {
            try (var cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(200).build())) {
                while (cursor.hasNext()) {
                    connection.del(cursor.next());
                }
            }
            return null;
        });
    }
}
