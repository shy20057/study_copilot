package com.example.studycopilot.service.attachment;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.config.StorageProperties;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

/** 私有头像对象存储；所有操作仅接受服务端生成的用户目录。 */
@Component
@RequiredArgsConstructor
public class AvatarObjectStore {
    private final StorageProperties properties;
    private volatile MinioClient client;

    private MinioClient client() {
        MinioClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    var http = new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS)
                            .callTimeout(30, TimeUnit.SECONDS).build();
                    current = MinioClient.builder().endpoint(properties.getEndpoint())
                            .credentials(properties.getAccessKey(), properties.getSecretKey()).httpClient(http).build();
                    client = current;
                }
            }
        }
        return current;
    }

    public static String prefix(long userId) {
        return "users/" + userId + "/avatars/";
    }

    public void put(long userId, String key, AvatarImage image) {
        checkOwner(userId, key);
        try (var input = new ByteArrayInputStream(image.bytes())) {
            client().putObject(PutObjectArgs.builder().bucket(properties.getBucket()).object(key)
                    .contentType(image.mediaType()).stream(input, image.bytes().length, -1).build());
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    public byte[] read(long userId, String key) {
        checkOwner(userId, key);
        try (var input = client().getObject(GetObjectArgs.builder().bucket(properties.getBucket()).object(key).build())) {
            byte[] bytes = input.readNBytes(AvatarImage.MAX_BYTES + 1);
            if (bytes.length > AvatarImage.MAX_BYTES) {
                throw unavailable();
            }
            return bytes;
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    public void remove(long userId, String key) {
        checkOwner(userId, key);
        try {
            client().removeObject(RemoveObjectArgs.builder().bucket(properties.getBucket()).object(key).build());
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    public void removeOwner(long userId) {
        try {
            var objects = client().listObjects(ListObjectsArgs.builder().bucket(properties.getBucket())
                    .prefix(prefix(userId)).recursive(true).build());
            for (var object : objects) {
                remove(userId, object.get().objectName());
            }
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    private void checkOwner(long userId, String key) {
        if (key == null || !key.startsWith(prefix(userId)) || key.contains("..")
                || key.substring(prefix(userId).length()).contains("/")) {
            throw new BusinessException(ScErrorCode.NOT_FOUND);
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(ScErrorCode.STORAGE_UNAVAILABLE);
    }
}
