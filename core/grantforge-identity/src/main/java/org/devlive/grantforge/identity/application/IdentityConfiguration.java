// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.Map;

/** Beans of the identity module. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SecurityProperties.class, SessionProperties.class, SetupProperties.class, MfaProperties.class})
public class IdentityConfiguration
{
    /** Prefix of new password hashes. */
    static final String DEFAULT_ENCODING = "argon2";

    /** Prefix of password hashes imported from the pre-rebuild server. */
    static final String LEGACY_ENCODING = "sha256-legacy";

    /**
     * Hashes new passwords with Argon2id and still verifies BCrypt and imported legacy SHA-256 hashes (D-24).
     * Stored hashes carry their algorithm as a {@code {id}} prefix, so the default can change without
     * invalidating existing passwords; outdated hashes are replaced at the next successful sign-in.
     *
     * @param properties security settings (the legacy character set)
     * @return the encoder
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder(SecurityProperties properties)
    {
        return new DelegatingPasswordEncoder(DEFAULT_ENCODING, Map.of(
                DEFAULT_ENCODING, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                "bcrypt", new BCryptPasswordEncoder(),
                LEGACY_ENCODING, new LegacySha256PasswordEncoder(properties.password().legacyCharset())));
    }

    /**
     * The clock used for timestamps in business logic, replaceable in tests.
     *
     * @return the UTC system clock
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock()
    {
        return Clock.systemUTC();
    }
}
