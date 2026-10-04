// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/** A single-use code that signs an account in when its authenticator is lost, kept as a hash. */
@Entity
@Table(name = "gf_mfa_recovery_code")
public class MfaRecoveryCode
        extends TenantScopedEntity
{
    @Column(name = "account_id", nullable = false, updatable = false)
    private long accountId;

    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private String codeHash = "";

    @Column(name = "used_at")
    private @Nullable Instant usedAt;

    /** For JPA. */
    protected MfaRecoveryCode()
    {
    }

    /**
     * Keeps a code.
     *
     * @param accountId the account
     * @param codeHash the code's SHA-256 hash
     * @return the code
     */
    public static MfaRecoveryCode of(long accountId, String codeHash)
    {
        MfaRecoveryCode code = new MfaRecoveryCode();
        code.accountId = accountId;
        code.codeHash = requireNonNull(codeHash, "codeHash");
        return code;
    }

    /**
     * Uses the code up.
     *
     * @param now the current time
     */
    public void use(Instant now)
    {
        this.usedAt = requireNonNull(now, "now");
    }

    /**
     * Returns the account.
     *
     * @return its ID
     */
    public long getAccountId()
    {
        return accountId;
    }

    /**
     * Returns the code's hash.
     *
     * @return the hash
     */
    public String getCodeHash()
    {
        return codeHash;
    }

    /**
     * Returns whether the code was used.
     *
     * @return {@code true} once used
     */
    public boolean isUsed()
    {
        return usedAt != null;
    }
}
