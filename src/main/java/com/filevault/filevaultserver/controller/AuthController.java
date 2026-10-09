package com.filevault.filevaultserver.controller;

import com.filevault.filevaultserver.action.auth.AuthResult;
import com.filevault.filevaultserver.action.auth.ChangeEmailAction;
import com.filevault.filevaultserver.action.auth.ChangePasswordAction;
import com.filevault.filevaultserver.action.auth.LoginAction;
import com.filevault.filevaultserver.action.auth.LogoutAction;
import com.filevault.filevaultserver.action.auth.MeAction;
import com.filevault.filevaultserver.action.auth.RefreshAction;
import com.filevault.filevaultserver.action.auth.RegisterAction;
import com.filevault.filevaultserver.middleware.ErrorResponse;
import com.filevault.filevaultserver.request.auth.ChangeEmailRequest;
import com.filevault.filevaultserver.request.auth.ChangePasswordRequest;
import com.filevault.filevaultserver.request.auth.LoginRequest;
import com.filevault.filevaultserver.request.auth.RegisterRequest;
import com.filevault.filevaultserver.response.auth.AuthResponse;
import com.filevault.filevaultserver.response.user.UserSummaryResponse;
import com.filevault.filevaultserver.security.RefreshTokenProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Registration, authentication, and session management")
@ApiResponses({
    @ApiResponse(
            responseCode = "429",
            description = "Too many requests",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class AuthController {

    private final RegisterAction registerAction;
    private final LoginAction loginAction;
    private final RefreshAction refreshAction;
    private final LogoutAction logoutAction;
    private final MeAction meAction;
    private final ChangeEmailAction changeEmailAction;
    private final ChangePasswordAction changePasswordAction;
    private final RefreshTokenProperties refreshTokenProperties;

    public AuthController(
            RegisterAction registerAction,
            LoginAction loginAction,
            RefreshAction refreshAction,
            LogoutAction logoutAction,
            MeAction meAction,
            ChangeEmailAction changeEmailAction,
            ChangePasswordAction changePasswordAction,
            RefreshTokenProperties refreshTokenProperties) {
        this.registerAction = registerAction;
        this.loginAction = loginAction;
        this.refreshAction = refreshAction;
        this.logoutAction = logoutAction;
        this.meAction = meAction;
        this.changeEmailAction = changeEmailAction;
        this.changePasswordAction = changePasswordAction;
        this.refreshTokenProperties = refreshTokenProperties;
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the current authenticated user, their roles and permissions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current user returned"),
        @ApiResponse(
                responseCode = "401",
                description = "Missing, invalid, or expired access token",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public UserSummaryResponse me(@AuthenticationPrincipal Jwt jwt) {
        return meAction.execute(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/me/email")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Change the account email",
            description = "Requires the current password as confirmation. Limited to 3 attempts per day.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Email changed"),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Missing/invalid access token, or currentPassword is wrong",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Another account already uses this email",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public UserSummaryResponse changeEmail(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangeEmailRequest request) {
        return changeEmailAction.execute(UUID.fromString(jwt.getSubject()), request);
    }

    @PutMapping("/me/password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Change the account password",
            description = "Requires the current password as confirmation, and revokes every other active "
                    + "refresh token (other sessions are logged out). Limited to 3 attempts per day.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password changed"),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Missing/invalid access token, or currentPassword is wrong",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        changePasswordAction.execute(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    @Operation(
            summary = "Create an account",
            description = "Always assigns the configured default signup role; there is no caller-supplied role.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created, tokens issued"),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error (malformed email, password too short/long, blank full name)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "An account with this email already exists",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withTokens(registerAction.execute(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authenticated, tokens issued"),
        @ApiResponse(
                responseCode = "400",
                description = "Validation error",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Email not found, password incorrect, or account disabled",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withTokens(loginAction.execute(request), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Exchange a refresh token cookie for a new access token",
            description = "Rotates the refresh token: the one presented is revoked and a new one is issued.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New tokens issued"),
        @ApiResponse(
                responseCode = "401",
                description = "Refresh token cookie missing, unknown, expired, or already revoked",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "${app.refresh-token.cookie-name}") String refreshToken) {
        return withTokens(refreshAction.execute(refreshToken), HttpStatus.OK);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current refresh token and clear its cookie")
    @ApiResponse(responseCode = "204", description = "Logged out (always, even without a refresh token cookie)")
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
        AuthResponse body = new AuthResponse(result.accessToken(), "Bearer", result.accessTokenTtlSeconds(), result.user());
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
