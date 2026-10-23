package com.example.studycopilot.dto.resp.onboarding;
import java.time.LocalDate;
import java.util.List;
/** Items are eligible candidates; summary totals describe only selected, not-yet-imported items. */
public record TemplatePreview(String templateId,String templateVersion,String previewVersion,
    String displayName,String description,LocalDate learningDate,List<Stage> stages,List<Item> items,
    int taskCount,int todayTaskCount,int estimatedFocusMinutes,boolean editableAfterImport) {
    public record Stage(String name,LocalDate startDate,LocalDate endDate,boolean executable) {}
    public record Item(String itemId,String title,String subjectAlias,long subjectId,long subjectVersion,
        LocalDate learningDate,int plannedPomodoros,int estimatedFocusMinutes,
        boolean alreadyImported,boolean selectedByDefault) {}
}
