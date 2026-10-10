package com.filevault.filevaultserver.action.auth;

import com.filevault.filevaultserver.exception.auth.EmailAlreadyUsedException;
import com.filevault.filevaultserver.models.Role;
import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.repository.role.RoleRepository;
import com.filevault.filevaultserver.repository.user.UserRepository;
import com.filevault.filevaultserver.request.auth.RegisterRequest;
import com.filevault.filevaultserver.security.SignupProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegisterAction {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignupProperties signupProperties;
    private final TokenIssuer tokenIssuer;

    public RegisterAction(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            SignupProperties signupProperties,
            TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.signupProperties = signupProperties;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public AuthResult execute(RegisterRequest request) {
        if (userRepository.existsByUsrEmail(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }
        // Self-registration is always pinned to the configured default role: there is no
        // caller-supplied role here, so there is no privilege-escalation surface to police.
        Role defaultRole = roleRepository
                .findByRolName(signupProperties.defaultRole())
                .orElseThrow(() -> new IllegalStateException(
                        "Default signup role not seeded: " + signupProperties.defaultRole()));
        User user = new User(request.email(), passwordEncoder.encode(request.password()), request.fullName());
        user.getRoles().add(defaultRole);
        userRepository.save(user);
        return tokenIssuer.issueTokens(user);
    }
}
