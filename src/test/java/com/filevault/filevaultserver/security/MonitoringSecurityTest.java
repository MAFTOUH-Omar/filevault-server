package com.filevault.filevaultserver.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filevault.filevaultserver.middleware.ErrorResponseWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.json.JsonMapper;

class MonitoringSecurityTest {

    private AbuseGuard abuseGuard;
    private MonitoringAuthenticationEntryPoint entryPoint;
    private MockHttpServletResponse response;
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    void setUp() {
        abuseGuard = mock(AbuseGuard.class);
        when(abuseGuard.deny(anyString(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> new AbuseGuard.Denial(
                        ((HttpStatus) invocation.getArgument(1)).value(),
                        invocation.getArgument(2),
                        invocation.getArgument(3)));
        entryPoint = new MonitoringAuthenticationEntryPoint(
                abuseGuard, new ErrorResponseWriter(JsonMapper.builder().build()));
        response = new MockHttpServletResponse();
    }

    @Test
    void wrongCredentialsCountAsAStrikeAndAskForBasicAuth() throws Exception {
        entryPoint.commence(request, response, new BadCredentialsException("bad"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Basic ");
        verify(abuseGuard).deny(anyString(), any(), anyString(), anyString());
    }

    @Test
    void sendingNoCredentialsIsNotAStrike() throws Exception {
        entryPoint.commence(request, response, new InsufficientAuthenticationException("none"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
        verify(abuseGuard, never()).deny(anyString(), any(), anyString(), anyString());
    }

    @Test
    void theResponseNeverEchoesTheExceptionMessage() throws Exception {
        entryPoint.commence(request, response, new BadCredentialsException("secret-internal-detail"));

        assertThat(response.getContentAsString()).doesNotContain("secret-internal-detail");
    }

    @Test
    void shortPasswordsAreRefusedAtStartup() {
        assertThatThrownBy(() -> new MonitoringProperties("monitor", "too-short", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("too-short");
    }

    @Test
    void emptyPasswordDisablesLoginAndALongOneEnablesIt() {
        assertThat(new MonitoringProperties(null, null, false).loginEnabled()).isFalse();
        assertThatCode(() -> new MonitoringProperties("monitor", "x".repeat(16), false)).doesNotThrowAnyException();
        assertThat(new MonitoringProperties("monitor", "x".repeat(16), false).loginEnabled()).isTrue();
    }

    @Test
    void thePasswordIsNeverPrinted() {
        assertThat(new MonitoringProperties("monitor", "super-secret-value-123", true).toString())
                .doesNotContain("super-secret-value-123");
    }
}
