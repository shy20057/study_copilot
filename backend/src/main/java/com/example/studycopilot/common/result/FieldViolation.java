package com.example.studycopilot.common.result;

import java.io.Serializable;

/** Public validation metadata intentionally excludes rejected values. */
public record FieldViolation(String field, String message) implements Serializable {}
