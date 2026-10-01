// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountTest
{
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Test
    void createKeepsTheNameAndDerivesTheCanonicalForm()
    {
        UserAccount account = UserAccount.create(" Alice.Admin ", "{argon2}hash", NOW);

        assertThat(account.getUsername()).isEqualTo("Alice.Admin");
        assertThat(account.getUsernameNorm()).isEqualTo("alice.admin");
        assertThat(account.getPasswordHash()).isEqualTo("{argon2}hash");
        assertThat(account.getPasswordChangedAt()).isEqualTo(NOW);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getDisplayName()).isNull();
        assertThat(account.getEmail()).isNull();
        assertThat(account.getLastLoginAt()).isNull();
        assertThat(account.isMustChangePassword()).isFalse();
        assertThat(account.isSystemAccount()).isFalse();
        assertThat(account.isLocked(NOW)).isFalse();
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void invalidInputIsRejected()
    {
        assertThatThrownBy(() -> UserAccount.create("ab", "h", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.create("a b c", "h", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.create("x".repeat(65), "h", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.create(null, "h", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.create("alice", " ", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.create("alice", "h", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void normalizeHandlesNullAndBlank()
    {
        assertThat(UserAccount.normalize(null)).isEmpty();
        assertThat(UserAccount.normalize("  ")).isEmpty();
        assertThat(UserAccount.normalize(" BoB ")).isEqualTo("bob");
    }

    @Test
    void optionalAttributesAndSystemFlag()
    {
        UserAccount account = UserAccount.create("alice", "h", NOW).withDisplayName("  Alice ").markSystemAccount();

        assertThat(account.getDisplayName()).isEqualTo("Alice");
        assertThat(account.isSystemAccount()).isTrue();
        assertThat(account.withDisplayName(" ").getDisplayName()).isNull();
    }

    @Test
    void reachingTheFailureLimitLocksTemporarily()
    {
        UserAccount account = UserAccount.create("alice", "h", NOW);
        Duration lock = Duration.ofMinutes(15);

        assertThat(account.recordFailedLogin(NOW, 3, lock)).isFalse();
        assertThat(account.recordFailedLogin(NOW, 3, lock)).isFalse();
        assertThat(account.getFailedAttempts()).isEqualTo(2);
        assertThat(account.recordFailedLogin(NOW, 3, lock)).isTrue();

        assertThat(account.getFailedAttempts()).isZero();
        assertThat(account.isLocked(NOW.plus(lock).minusSeconds(1))).isTrue();
        assertThat(account.isLocked(NOW.plus(lock))).isFalse();
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void failureRecordingValidatesItsArguments()
    {
        UserAccount account = UserAccount.create("alice", "h", NOW);
        Duration lock = Duration.ofMinutes(1);

        assertThatThrownBy(() -> account.recordFailedLogin(NOW, 0, lock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.recordFailedLogin(null, 1, lock)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> account.recordFailedLogin(NOW, 1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void successAndPasswordChangeClearLockout()
    {
        UserAccount account = UserAccount.create("alice", "h", NOW);
        account.recordFailedLogin(NOW, 1, Duration.ofHours(1));

        account.recordSuccessfulLogin(NOW.plusSeconds(5));
        assertThat(account.isLocked(NOW.plusSeconds(6))).isFalse();
        assertThat(account.getLastLoginAt()).isEqualTo(NOW.plusSeconds(5));

        account.recordFailedLogin(NOW, 1, Duration.ofHours(1));
        account.changePassword("{argon2}new", NOW.plusSeconds(10));
        assertThat(account.isLocked(NOW.plusSeconds(11))).isFalse();
        assertThat(account.getPasswordHash()).isEqualTo("{argon2}new");
        assertThat(account.getPasswordChangedAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(account.isMustChangePassword()).isFalse();
        assertThatThrownBy(() -> account.changePassword(" ", NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehashKeepsThePasswordAge()
    {
        UserAccount account = UserAccount.create("alice", "{bcrypt}old", NOW);

        account.rehashPassword("{argon2}new");

        assertThat(account.getPasswordHash()).isEqualTo("{argon2}new");
        assertThat(account.getPasswordChangedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> account.rehashPassword("")).isInstanceOf(IllegalArgumentException.class);
    }
}
