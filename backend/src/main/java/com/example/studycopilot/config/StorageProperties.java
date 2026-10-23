package com.example.studycopilot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 头像存储配置；启动时不访问对象存储。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "study-copilot.storage")
public class StorageProperties {
    private String endpoint;
    private String bucket;
    private String accessKey;
    private String secretKey;
}
