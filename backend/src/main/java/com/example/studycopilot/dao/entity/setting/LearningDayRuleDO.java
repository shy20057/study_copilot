package com.example.studycopilot.dao.entity.setting;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.Instant;
@Data @TableName("study_copilot.t_learning_day_rule")
public class LearningDayRuleDO {
    @TableId(type=IdType.AUTO) private Long id;
    private Long userId;
    private Long version;
    private String timezone;
    private Integer dayStartHour;
    private Instant effectiveAt;
    private Instant createTime;
}
