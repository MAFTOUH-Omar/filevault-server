package com.filevault.filevaultserver.action.auth;

import com.filevault.filevaultserver.models.Role;
import com.filevault.filevaultserver.models.User;
import com.filevault.filevaultserver.repository.RoleRepository;
import com.filevault.filevaultserver.repository.UserRepository;
import com.filevault.filevaultserver.security.BootstrapProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the initial admin account from app.bootstrap.admin-email/admin-password on startup,
 * so a fresh environment always has one administrator without a manual SQL step.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final String ADMIN_ROLE_NAME = "admin";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties bootstrapProperties;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            BootstrapProperties bootstrapProperties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapProperties = bootstrapProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = bootstrapProperties.adminEmail();
        String password = bootstrapProperties.adminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (userRepository.existsByUsrEmail(email)) {
            return;
        }
        Role adminRole = roleRepository
                .findByRolName(ADMIN_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException("'" + ADMIN_ROLE_NAME + "' role not seeded"));
        User admin = new User(email, passwordEncoder.encode(password), "Administrator");
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
        log.info("Bootstrap admin user created: {}", email);
    }
}
