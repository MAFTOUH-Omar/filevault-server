package com.filevault.filevaultserver.middleware;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.filevault.filevaultserver.security.AbuseGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private static final String SECRET = "SELECT password FROM usr_users";

    @RestController
    static class ProbeController {

        record Body(@NotBlank String name) {
        }

        @GetMapping("/probe/long/{id}")
        String byId(@PathVariable("id") Long id) {
            return "ok";
        }

        @PostMapping("/probe/body")
        String body(@Valid @RequestBody Body body) {
            return "ok";
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException(SECRET + " at com.filevault.Internal");
        }

        @GetMapping("/probe/db")
        String db() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint uk_usr_email");
        }

        @GetMapping("/probe/denied")
        String denied() {
            throw new AccessDeniedException("Access Denied");
        }
    }

    private MockMvc mockMvc;
    private AbuseGuard abuseGuard;

    @BeforeEach
    void setUp() {
        abuseGuard = mock(AbuseGuard.class);
        when(abuseGuard.deny(anyString(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> new AbuseGuard.Denial(
                        ((HttpStatus) invocation.getArgument(1)).value(),
                        invocation.getArgument(2),
                        invocation.getArgument(3)));
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler(abuseGuard))
                .build();
    }

    @Test
    void typeMismatchNeverNamesJavaTypes() throws Exception {
        mockMvc.perform(get("/probe/long/{id}", "{id}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("java.lang"))));
    }

    @Test
    void malformedJsonIsGeneric() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request"));
    }

    @Test
    void beanValidationKeepsFieldMessages() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("name:")));
    }

    @Test
    void unexpectedExceptionNeverLeaksItsMessage() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("com.filevault"))));
    }

    @Test
    void databaseErrorNeverLeaksConstraintNames() throws Exception {
        mockMvc.perform(get("/probe/db"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("uk_usr_email"))));
    }

    @Test
    void wrongMethodIsGenericAndKeepsAllowHeader() throws Exception {
        mockMvc.perform(post("/probe/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("Method not allowed"));
    }

    @Test
    void accessDeniedIs403AndCountsAsAStrike() throws Exception {
        mockMvc.perform(get("/probe/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"));
        org.mockito.Mockito.verify(abuseGuard).deny(anyString(), eq(HttpStatus.FORBIDDEN), eq("FORBIDDEN"), eq("Access denied"));
    }
}
