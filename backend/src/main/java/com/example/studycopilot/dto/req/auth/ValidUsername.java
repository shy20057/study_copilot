package com.example.studycopilot.dto.req.auth;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;
import java.util.Locale;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidUsername.Validator.class)
public @interface ValidUsername {
    String message() default "用户名须为1至50个字符";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidUsername, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return false;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            return !normalized.isBlank() && normalized.codePointCount(0, normalized.length()) <= 50;
        }
    }
}
