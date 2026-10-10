package com.filevault.filevaultserver.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Separate filter chain for {@code /actuator/**}, evaluated before the JWT chain in
 * {@link SecurityConfig}. {@code health} and {@code info} are public (load balancers, uptime checks);
 * every other endpoint needs HTTP Basic as the single monitoring user, whose password comes from
 * {@code MONITORING_PASSWORD}. It has its own AuthenticationManager so the monitoring account can never
 * be used on the API, and API users/tokens never work here.
 *
 * Which endpoints exist at all is decided by {@code management.endpoints.web.exposure.include}
 * ({@code ACTUATOR_EXPOSE}); this chain only decides who may reach the exposed ones.
 */
@Configuration
public class MonitoringSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain monitoringFilterChain(
            HttpSecurity http,
            MonitoringProperties properties,
            PasswordEncoder passwordEncoder,
            MonitoringAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        List<UserDetails> users = properties.loginEnabled()
                ? List.of(User.withUsername(properties.username())
                        .password(passwordEncoder.encode(properties.password()))
                        .roles("MONITOR")
                        .build())
                : List.of();
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new InMemoryUserDetailsManager(users));
        provider.setPasswordEncoder(passwordEncoder);

        http.securityMatcher("/actuator/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationManager(new ProviderManager(provider))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .anyRequest().hasRole("MONITOR"))
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
