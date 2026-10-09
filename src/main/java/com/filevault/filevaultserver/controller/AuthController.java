package com.filevault.filevaultserver.controller;

import com.filevault.filevaultserver.action.auth.AuthResult;
import com.filevault.filevaultserver.action.auth.LoginAction;
import com.filevault.filevaultserver.action.auth.LogoutAction;
import com.filevault.filevaultserver.action.auth.RefreshAction;
import com.filevault.filevaultserver.action.auth.RegisterAction;
import com.filevault.filevaultserver.request.auth.LoginRequest;
import com.filevault.filevaultserver.request.auth.RegisterRequest;
import com.filevault.filevaultserver.response.auth.AuthResponse;
import com.filevault.filevaultserver.security.RefreshTokenProperties;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterAction registerAction;
    private final LoginAction loginAction;
    private final RefreshAction refreshAction;
    private final LogoutAction logoutAction;
    private final RefreshTokenProperties refreshTokenProperties;

    public AuthController(
            RegisterAction registerAction,
            LoginAction loginAction,
            RefreshAction refreshAction,
            LogoutAction logoutAction,
            RefreshTokenProperties refreshTokenProperties) {
        this.registerAction = registerAction;
        this.loginAction = loginAction;
        this.refreshAction = refreshAction;
        this.logoutAction = logoutAction;
        this.refreshTokenProperties = refreshTokenProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withTokens(registerAction.execute(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withTokens(loginAction.execute(request), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "${app.refresh-token.cookie-name}") String refreshToken) {
        return withTokens(refreshAction.execute(refreshToken), HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${app.refresh-token.cookie-name}", required = false) String refreshToken) {
        if (refreshToken != null) {
            logoutAction.execute(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withTokens(AuthResult result, HttpStatus status) {
        ResponseCookie cookie = ResponseCookie.from(refreshTokenProperties.cookieName(), result.rawRefreshToken())
                .httpOnly(true)
                .secure(refreshTokenProperties.cookieSecure())
                .sameSite(refreshTokenProperties.cookieSameSite())
                .path("/auth")
                .maxAge(refreshTokenProperties.ttl())
                .build();
        AuthResponse body = new AuthResponse(result.accessToken(), "Bearer", result.accessTokenTtlSeconds());
        return ResponseEntity.status(status).header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
    }

    private ResponseCookie expiredCookie() {
        return ResponseCookie.from(refreshTokenProperties.cookieName(), "")
                .httpOnly(true)
                .secure(refreshTokenProperties.cookieSecure())
                .sameSite(refreshTokenProperties.cookieSameSite())
                .path("/auth")
                .maxAge(0)
                .build();
    }
}
