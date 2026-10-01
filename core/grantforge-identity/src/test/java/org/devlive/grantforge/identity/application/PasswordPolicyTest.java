// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest
{
    private final PasswordPolicy policy = policy(1);

    private static PasswordPolicy policy(int characterClasses)
    {
        return new PasswordPolicy(new SecurityProperties(false,
                new SecurityProperties.Password(10, 20, characterClasses, 0, null, StandardCharsets.UTF_8),
                SecurityProperties.Lockout.defaults()));
    }

    @Test
    void acceptsPasswordsWithinTheLimits()
    {
        assertThatCode(() -> policy.check("0123456789", "alice")).doesNotThrowAnyException();
        assertThatCode(() -> policy.check("x".repeat(20), null)).doesNotThrowAnyException();
        // Short names are not searched for: "al" appears in many good passwords.
        assertThatCode(() -> policy.check("totally-alright", "al")).doesNotThrowAnyException();
    }

    @Test
    void lengthCountsCharactersNotUtf16Units()
    {
        // Ten emoji are twenty UTF-16 units but ten characters.
        assertThatCode(() -> policy.check("🔐".repeat(10), null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.check("🔐".repeat(9), null)).satisfies(error ->
                assertThat(((GrantForgeException) error).getErrorCode()).isEqualTo(IdentityErrorCode.PASSWORD_TOO_SHORT));
    }

    @Test
    void reportsTheBrokenRuleWithItsLimit()
    {
        assertThatThrownBy(() -> policy.check(null, null)).isInstanceOfSatisfying(GrantForgeException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.PASSWORD_TOO_SHORT);
            assertThat(error.getArguments()).containsExactly(10);
        });
        assertThatThrownBy(() -> policy.check("x".repeat(21), null)).isInstanceOfSatisfying(GrantForgeException.class,
                error -> {
                    assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.PASSWORD_TOO_LONG);
                    assertThat(error.getArguments()).containsExactly(20);
                });
        assertThatThrownBy(() -> policy.check("my-ALICE-secret", " Alice ")).isInstanceOfSatisfying(
                GrantForgeException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.PASSWORD_CONTAINS_USERNAME));
    }

    @Test
    void requiredCharacterClassesAreCounted()
    {
        assertThat(PasswordPolicy.characterClasses("abc")).isEqualTo(1);
        assertThat(PasswordPolicy.characterClasses("aB3!")).isEqualTo(4);
        assertThat(PasswordPolicy.characterClasses("密码🔐")).isEqualTo(1);
        assertThat(PasswordPolicy.characterClasses("ÄÖÜäöü")).isEqualTo(2);

        PasswordPolicy strict = policy(3);
        assertThatCode(() -> strict.check("Correct-horse", null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> strict.check("correct-horse", null)).isInstanceOfSatisfying(GrantForgeException.class,
                error -> {
                    assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.PASSWORD_TOO_SIMPLE);
                    assertThat(error.getArguments()).containsExactly(3);
                });
    }
}
