// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityPropertiesTest
{
    private static SecurityProperties bind(Map<String, String> values)
    {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("grantforge.security", SecurityProperties.class);
    }

    private static SecurityProperties.Password password(int min, int max, int classes, int history,
            @Nullable Duration maxAge)
    {
        return new SecurityProperties.Password(min, max, classes, history, maxAge, StandardCharsets.UTF_8);
    }

    @Test
    void defaultsKeepRegistrationOffAndRequireTwelveCharacters()
    {
        SecurityProperties properties = bind(Map.of());

        assertThat(properties.registrationEnabled()).isFalse();
        assertThat(properties.password().minLength()).isEqualTo(12);
        assertThat(properties.password().maxLength()).isEqualTo(128);
        assertThat(properties.password()).isEqualTo(SecurityProperties.Password.defaults());
    }

    @Test
    void valuesAreBound()
    {
        SecurityProperties properties = bind(Map.of("grantforge.security.registration-enabled", "true",
                "grantforge.security.password.min-length", "16",
                "grantforge.security.password.required-character-classes", "3",
                "grantforge.security.password.history-size", "5",
                "grantforge.security.password.max-age", "90d",
                "grantforge.security.password.legacy-charset", "GBK"));

        assertThat(properties.registrationEnabled()).isTrue();
        assertThat(properties.password()).isEqualTo(new SecurityProperties.Password(16, 128, 3, 5, Duration.ofDays(90),
                Charset.forName("GBK")));
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void invalidPoliciesAreRejected()
    {
        assertThatThrownBy(() -> password(7, 128, 1, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> password(20, 19, 1, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> password(8, 1025, 1, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> password(12, 128, 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> password(12, 128, 5, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> password(12, 128, 1, -1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> password(12, 128, 1, 25, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> password(12, 128, 1, 0, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> password(12, 128, 1, 0, Duration.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties.Password(12, 128, 1, 0, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SecurityProperties(false, null)).isInstanceOf(NullPointerException.class);
    }
}
