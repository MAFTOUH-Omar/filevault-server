package com.filevault.filevaultserver.action.auth;

import com.filevault.filevaultserver.exception.auth.InvalidCredentialsException;
import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.repository.user.UserRepository;
import com.filevault.filevaultserver.request.auth.LoginRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginAction {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    public LoginAction(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public AuthResult execute(LoginRequest request) {
        User user = userRepository
                .findByUsrEmail(request.email())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getUsrPasswordHash()))
                .filter(User::isUsrEnabled)
                .orElseThrow(InvalidCredentialsException::new);
        return tokenIssuer.issueTokens(user);
    }
}
