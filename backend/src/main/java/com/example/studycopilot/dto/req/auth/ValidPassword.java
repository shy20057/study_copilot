package com.example.studycopilot.dto.req.auth;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;
import java.nio.charset.StandardCharsets;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPassword.Validator.class)
public @interface ValidPassword {
    String message() default "密码须为8至64个字符且UTF-8不超过72字节";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPassword, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) { return valid(value); }

        public static boolean valid(String value) {
            if (value == null) return false;
            int count = value.codePointCount(0, value.length());
            // Reject unpaired surrogates so UTF-8 represents the supplied password exactly.
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isHighSurrogate(c)) {
                    if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
                } else if (Character.isLowSurrogate(c)) return false;
            }
            return count >= 8 && count <= 64 && value.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }
}
