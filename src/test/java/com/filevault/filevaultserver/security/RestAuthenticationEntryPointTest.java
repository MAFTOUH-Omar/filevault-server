package com.filevault.filevaultserver.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filevault.filevaultserver.middleware.ErrorResponseWriter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import tools.jackson.databind.json.JsonMapper;

class RestAuthenticationEntryPointTest {

    private AbuseGuard abuseGuard;
    private RestAuthenticationEntryPoint entryPoint;
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
        entryPoint = new RestAuthenticationEntryPoint(abuseGuard, new ErrorResponseWriter(JsonMapper.builder().build()));
        response = new MockHttpServletResponse();
    }

    @Test
    void missingTokenIsNotAStrike() throws Exception {
        entryPoint.commence(request, response, new InsufficientAuthenticationException("no creds"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
        verify(abuseGuard, never()).deny(anyString(), any(), anyString(), anyString());
    }

    @Test
    void expiredTokenIsNotAStrike() throws Exception {
        var expired = new JwtValidationException(
                "bad", List.of(new OAuth2Error("invalid_token", "Jwt expired at 2026-01-01T00:00:00Z", null)));

        entryPoint.commence(request, response, new InvalidBearerTokenException("expired", expired));

        assertThat(response.getStatus()).isEqualTo(401);
        verify(abuseGuard, never()).deny(anyString(), any(), anyString(), anyString());
    }

    @Test
    void forgedTokenIsAStrikeAndNeverEchoesDecoderInternals() throws Exception {
        entryPoint.commence(
                request, response, new InvalidBearerTokenException("An error occurred while attempting to decode the Jwt: Signed JWT rejected"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain("Signed JWT").doesNotContain("decode");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        verify(abuseGuard).deny(anyString(), any(), anyString(), anyString());
    }
}
