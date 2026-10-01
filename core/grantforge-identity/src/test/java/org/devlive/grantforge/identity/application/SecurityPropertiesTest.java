// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

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

    @Test
    void defaultsKeepRegistrationOffAndRequireTwelveCharacters()
    {
        SecurityProperties properties = bind(Map.of());

        assertThat(properties.registrationEnabled()).isFalse();
        assertThat(properties.password().minLength()).isEqualTo(12);
        assertThat(properties.password().maxLength()).isEqualTo(128);
    }

    @Test
    void valuesAreBound()
    {
        SecurityProperties properties = bind(Map.of("grantforge.security.registration-enabled", "true",
                "grantforge.security.password.min-length", "16"));

        assertThat(properties.registrationEnabled()).isTrue();
        assertThat(properties.password().minLength()).isEqualTo(16);
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void invalidPoliciesAreRejected()
    {
        assertThatThrownBy(() -> new SecurityProperties.Password(7, 128)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties.Password(20, 19)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties.Password(8, 1025)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties(false, null)).isInstanceOf(NullPointerException.class);
    }
}
