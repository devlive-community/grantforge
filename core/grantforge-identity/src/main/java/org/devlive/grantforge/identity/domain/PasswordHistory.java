// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;

/** A password an account used before, kept as its hash so a policy can forbid reusing it. */
@Entity
@Table(name = "gf_password_history")
public class PasswordHistory
        extends TenantScopedEntity
{
    @Column(name = "account_id", nullable = false, updatable = false)
    private long accountId;

    @Column(name = "password_hash", nullable = false, updatable = false, length = 255)
    private String passwordHash = "";

    /** For JPA. */
    protected PasswordHistory()
    {
    }

    /**
     * Remembers a former password.
     *
     * @param accountId the account that used the password
     * @param passwordHash the hash it had
     * @return the entry
     */
    public static PasswordHistory of(long accountId, String passwordHash)
    {
        PasswordHistory entry = new PasswordHistory();
        entry.accountId = accountId;
        entry.passwordHash = Strings.requireNonBlank(passwordHash, "passwordHash");
        return entry;
    }

    /**
     * Returns the account.
     *
     * @return the account ID
     */
    public long getAccountId()
    {
        return accountId;
    }

    /**
     * Returns the former hash.
     *
     * @return the hash
     */
    public String getPasswordHash()
    {
        return passwordHash;
    }
}
