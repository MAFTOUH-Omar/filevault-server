package com.filevault.filevaultserver.auth;

import com.filevault.filevaultserver.auth.dto.LoginRequest;
import com.filevault.filevaultserver.auth.dto.RegisterRequest;
import com.filevault.filevaultserver.role.Role;
import com.filevault.filevaultserver.role.RoleRepository;
import com.filevault.filevaultserver.security.JwtProperties;
import com.filevault.filevaultserver.security.JwtService;
import com.filevault.filevaultserver.security.RefreshTokenProperties;
import com.filevault.filevaultserver.security.SignupProperties;
import com.filevault.filevaultserver.user.User;
import com.filevault.filevaultserver.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenProperties refreshTokenProperties;
    private final SignupProperties signupProperties;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            RefreshTokenProperties refreshTokenProperties,
            SignupProperties signupProperties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenProperties = refreshTokenProperties;
        this.signupProperties = signupProperties;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (userRepository.existsByUsrEmail(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }
        Role defaultRole = roleRepository.findByRolName(signupProperties.defaultRole())
                .orElseThrow(() -> new IllegalStateException(
                        "Default signup role not seeded: " + signupProperties.defaultRole()));
        User user = new User(request.email(), passwordEncoder.encode(request.password()), request.fullName());
        user.getRoles().add(defaultRole);
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByUsrEmail(request.email())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getUsrPasswordHash()))
                .filter(User::isUsrEnabled)
                .orElseThrow(InvalidCredentialsException::new);
        return issueTokens(user);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokenRepository.findByTokTokenHash(hash(rawRefreshToken))
                .filter(RefreshToken::isActive)
                .orElseThrow(InvalidRefreshTokenException::new);
        token.revoke();
        return issueTokens(token.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokTokenHash(hash(rawRefreshToken)).ifPresent(RefreshToken::revoke);
    }

    private AuthResult issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken(
                user, hash(rawRefreshToken), Instant.now().plus(refreshTokenProperties.ttl()));
        refreshTokenRepository.save(refreshToken);
        return new AuthResult(accessToken, jwtProperties.accessTokenTtl().toSeconds(), rawRefreshToken);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
