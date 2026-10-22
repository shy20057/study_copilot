package com.example.studycopilot.dao.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter @Setter
@TableName("study_copilot.t_refresh_session")
public class RefreshSessionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String tokenHash;
    private String deviceDescription;
    private Long sessionVersion;
    private Instant expiresAt;
    private Instant revokedAt;
    private Instant createTime;
}
