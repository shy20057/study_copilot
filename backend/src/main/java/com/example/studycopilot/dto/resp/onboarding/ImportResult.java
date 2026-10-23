package com.example.studycopilot.dto.resp.onboarding;
import java.util.*;
public record ImportResult(String status,long version,UUID importBatchId,List<Long> taskIds,List<String> skippedItemIds) {}
