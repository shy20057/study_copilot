package com.example.studycopilot.dto.req.subject;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class SubjectStateRequest {
    @NotNull @Min(0) private Long version;
    @Pattern(regexp="MIGRATE|CANCEL") private String strategy;
    private Long targetSubjectId;
}
