package com.filevault.filevaultserver.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorsOriginsTest {

    @Test
    void splitsOnCommasAndTrimsWhitespace() {
        assertThat(SecurityConfig.parseOrigins("https://filevault-client.vercel.app, http://localhost:3000"))
                .containsExactly("https://filevault-client.vercel.app", "http://localhost:3000");
    }

    @Test
    void dropsTrailingSlashesBecauseBrowsersNeverSendThem() {
        assertThat(SecurityConfig.parseOrigins("https://filevault-client.vercel.app/,http://localhost:3000//"))
                .containsExactly("https://filevault-client.vercel.app", "http://localhost:3000");
    }

    @Test
    void ignoresEmptyEntries() {
        assertThat(SecurityConfig.parseOrigins(" , http://localhost:3000,,")).containsExactly("http://localhost:3000");
        assertThat(SecurityConfig.parseOrigins("")).isEmpty();
    }
}
