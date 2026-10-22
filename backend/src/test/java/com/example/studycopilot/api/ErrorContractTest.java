package com.example.studycopilot.api;

import com.example.studycopilot.common.global.GlobalExceptionHandler;
import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.common.exception.ClientException;
import com.example.studycopilot.common.exception.NotFoundException;
import com.example.studycopilot.common.exception.ServiceException;
import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.support.trace.TraceIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ErrorContractTest {
    private MockMvc mvc;
    private ErrorProbeController controller;

    @BeforeEach
    void setUp() {
        controller = new ErrorProbeController();
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter()).build();
    }

    @Test
    void validationReturns400AndFieldErrors() throws Exception {
        mvc.perform(post("/test/error/validation").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SC_001001"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    void successExposesTheRequiredMetadata() throws Exception {
        mvc.perform(get("/test/error/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "VALIDATION_ERROR,SC_001001,400,false", "UNAUTHORIZED,SC_002006,401,false",
            "AUTH_REQUIRED,SC_002006,401,false", "TOKEN_INVALID,SC_002005,401,false",
            "TOKEN_EXPIRED,SC_002004,401,false", "TOKEN_REVOKED,SC_002008,401,false",
            "USER_PASSWORD_ERROR,SC_002003,401,false", "USER_DISABLED,SC_002002,403,false",
            "FORBIDDEN,SC_002007,403,false", "NOT_FOUND,SC_001002,404,false",
            "USER_NOT_FOUND,SC_002001,404,false", "SUBJECT_NOT_FOUND,SC_004001,404,false",
            "SETTING_NOT_FOUND,SC_003001,404,false", "TASK_NOT_FOUND,SC_006001,404,false",
            "VERSION_CONFLICT,SC_001005,409,false", "IDEMPOTENCY_CONFLICT,SC_001006,409,false",
            "SUBJECT_IN_USE,SC_004003,409,false", "LAST_ACTIVE_SUBJECT,SC_004004,409,false",
            "RULE_CHANGE_BLOCKED,SC_003002,409,false", "SESSION_EXISTS,SC_007004,409,false",
            "SESSION_NOT_DUE,SC_007005,409,false", "TIME_OVERLAP,SC_007006,409,false",
            "SUBJECT_DUPLICATE,SC_004002,409,false", "RATE_LIMIT,SC_001007,429,true",
            "AI_RATE_LIMIT,SC_010001,429,true", "SYSTEM_ERROR,SC_001000,500,true"
    })
    void domainCodesMapToHttpStatuses(String name, String code, int status, boolean retryable) throws Exception {
        var response = mvc.perform(get("/test/error/domain/" + name).header("X-Trace-Id", "domain-trace"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.retryable").value(retryable))
                .andExpect(header().string("X-Trace-Id", "domain-trace"))
                .andExpect(jsonPath("$.traceId").value("domain-trace"))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private-exception-marker");
        assertThat(controller.errorTrace).isEqualTo("domain-trace");
        assertThat(MDC.get("traceId")).isNull();
    }

    @ParameterizedTest
    @CsvSource({"authentication,401,SC_002006", "forbidden,403,SC_002007",
            "missing,404,SC_001002", "client,400,SC_001001", "service,500,SC_001000",
            "unexpected,500,SC_001000"})
    void exceptionFamiliesAreSanitized(String kind, int httpStatus, String code) throws Exception {
        var response = mvc.perform(get("/test/error/exception/" + kind))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private-exception-marker");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(json(response.getContentAsString()).get("traceId").asText());
        assertThat(controller.errorTrace).isEqualTo(response.getHeader("X-Trace-Id"));
        assertThat(MDC.get("traceId")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "trace_1.foo-2", "1234567890123456789012345678901234567890123456789012345678901234"})
    void acceptsSafeTraceIdsAndBindsMdc(String traceId) throws Exception {
        mvc.perform(get("/test/error/success").header("X-Trace-Id", traceId))
                .andExpect(header().string("X-Trace-Id", traceId))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.data.mdc").value(traceId));
        assertThat(MDC.get("traceId")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "bad trace", "bad\r\nInjected:value", "<script>", "你好",
            "12345678901234567890123456789012345678901234567890123456789012345"})
    void replacesUnsafeTraceIdsWithUuids(String incoming) throws Exception {
        var response = mvc.perform(get("/test/error/success").header("X-Trace-Id", incoming)).andReturn().getResponse();
        String traceId = response.getHeader("X-Trace-Id");
        assertThat(UUID.fromString(traceId).toString()).isEqualTo(traceId);
        assertThat(json(response.getContentAsString()).get("traceId").asText()).isEqualTo(traceId);
        assertThat(json(response.getContentAsString()).path("data").path("mdc").asText()).isEqualTo(traceId);
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void missingOrAmbiguousTraceHeadersGenerateDifferentRequestIds() throws Exception {
        var first = mvc.perform(get("/test/error/success")).andReturn().getResponse();
        var second = mvc.perform(get("/test/error/success").header("X-Trace-Id", "first", "second"))
                .andReturn().getResponse();
        assertThat(UUID.fromString(first.getHeader("X-Trace-Id")))
                .isNotEqualTo(UUID.fromString(second.getHeader("X-Trace-Id")));
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void envelopeAlwaysContainsAllEightFieldsWithoutLegacyJsonProperties() throws Exception {
        var response = mvc.perform(get("/test/error/success")).andReturn().getResponse();
        assertThat(json(response.getContentAsString()).properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("code", "message", "data", "traceId", "retryable", "fieldErrors", "currentVersion", "currentSession");
    }

    @Test
    void versionConflictCarriesTheCurrentVersion() throws Exception {
        mvc.perform(get("/test/error/version"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(7))
                .andExpect(jsonPath("$.currentSession").isEmpty());
    }

    @Test
    void sessionConflictsCarryTheCurrentSession() throws Exception {
        for (String name : new String[]{"SESSION_EXISTS", "SESSION_NOT_DUE"}) {
            mvc.perform(get("/test/error/session/" + name))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.currentSession.sessionId").value(42))
                    .andExpect(jsonPath("$.currentSession.remainingSeconds").value(60))
                    .andExpect(jsonPath("$.currentVersion").isEmpty());
        }
    }

    @Test
    void requestParsingFailuresUse400WithoutEchoingRejectedValues() throws Exception {
        mvc.perform(post("/test/error/validation").contentType(APPLICATION_JSON).content("private-rejected-marker"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SC_001001"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-rejected-marker"))));
        mvc.perform(get("/test/error/parameter").param("count", "private-rejected-marker"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("count"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-rejected-marker"))));
        mvc.perform(get("/test/error/parameter"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("count"));
    }

    @Test
    void fieldErrorsNeverExposeRejectedValuesOrInterpolatedValidationMessages() throws Exception {
        var response = mvc.perform(post("/test/error/sensitive-validation").contentType(APPLICATION_JSON)
                        .content("{\"secret\":\"private-rejected-marker\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("secret"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty())
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private-rejected-marker", "private-validation-marker", "rejectedValue");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(json(response.getContentAsString()).get("traceId").asText());
    }

    @Test
    void frameworkMissingRouteAndWrongMethodUseEnvelopes() throws Exception {
        mvc.perform(get("/no-such-route")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SC_001002"));
        mvc.perform(post("/test/error/success")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("SC_001004"));
    }

    private JsonNode json(String content) throws Exception {
        return new ObjectMapper().readTree(content);
    }

    @RestController
    @RequestMapping("/test/error")
    static class ErrorProbeController {
        private String errorTrace;
        @PostMapping("/validation")
        Result<Void> validation(@Valid @RequestBody ProbeInput input) {
            return Result.success();
        }

        @PostMapping("/sensitive-validation")
        Result<Void> sensitiveValidation(@Valid @RequestBody SensitiveInput input) {
            return Result.success();
        }

        @GetMapping("/success")
        Result<Map<String, String>> success() {
            return Result.success(Map.of("mdc", String.valueOf(MDC.get("traceId"))));
        }

        @GetMapping("/domain/{name}")
        Result<Void> domain(@PathVariable("name") String name) {
            errorTrace = MDC.get("traceId");
            throw new BusinessException(ScErrorCode.valueOf(name).code(), "private-exception-marker");
        }

        @GetMapping("/exception/{kind}")
        Result<Void> exception(@PathVariable("kind") String kind) {
            errorTrace = MDC.get("traceId");
            throw switch (kind) {
                case "authentication" -> new BadCredentialsException("private-exception-marker");
                case "forbidden" -> new AccessDeniedException("private-exception-marker");
                case "missing" -> new NotFoundException("custom", "private-exception-marker");
                case "client" -> new ClientException("custom", "private-exception-marker");
                case "service" -> new ServiceException("custom", "private-exception-marker");
                default -> new IllegalStateException("private-exception-marker SQL /internal/path");
            };
        }

        @GetMapping("/version")
        Result<Void> version() {
            throw new BusinessException(ScErrorCode.VERSION_CONFLICT, 7L, null);
        }

        @GetMapping("/session/{name}")
        Result<Void> session(@PathVariable("name") String name) {
            throw new BusinessException(ScErrorCode.valueOf(name), null, Map.of("sessionId", 42L, "remainingSeconds", 60));
        }

        @GetMapping("/parameter")
        Result<Void> parameter(@RequestParam("count") int count) {
            return Result.success();
        }
    }

    record ProbeInput(@NotBlank String title) {}
    record SensitiveInput(@Pattern(regexp = "allowed", message = "private-validation-marker ${validatedValue}") String secret) {}
}
