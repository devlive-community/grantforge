// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.jspecify.annotations.Nullable;

/**
 * DirectoryAccount owned by one tenant's organization data source.
 */
@Entity
@Table(name = "gf_directory_account")
public class DirectoryAccount extends TenantScopedEntity
{
    @Column(name = "source_id", nullable = false, updatable = false)
    private long sourceId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private long accountId;

    @Column(name = "trusted_email", length = 254)
    private @Nullable String trustedEmail;

    @Column(name = "employee_id", length = 255)
    private @Nullable String employeeId;

    /**
     * For JPA.
     */
    protected DirectoryAccount()
    {
    }

    /**
     * Remembers an authenticated OIDC account before its directory has necessarily been fetched.
     *
     * @param source  the data source
     * @param account the login account
     * @return the pending binding
     */
    public static DirectoryAccount of(long source, long account)
    {
        DirectoryAccount link = new DirectoryAccount();
        link.sourceId = source;
        link.accountId = account;
        return link;
    }

    /**
     * Remembers only a verified provider claim, never editable account data.
     *
     * @param email the login claim
     */
    public void remember(@Nullable String email)
    {
        trustedEmail = DirectoryEmployee.normalizeEmail(email);
    }

    /**
     * Binds once; mailbox changes must not transfer an account.
     *
     * @param employee the stable external employee ID
     * @throws IllegalStateException if a caller attempts to rebind the account
     */
    public void bind(String employee)
    {
        if (employeeId != null && !employeeId.equals(employee)) {
            throw new IllegalStateException("directory account cannot be rebound");
        }
        employeeId = employee;
    }

    /**
     * Returns sourceId.
     *
     * @return the value
     */
    public long getSourceId()
    {
        return sourceId;
    }

    /**
     * Returns accountId.
     *
     * @return the value
     */
    public long getAccountId()
    {
        return accountId;
    }

    /**
     * Returns trustedEmail.
     *
     * @return the value
     */
    public @Nullable String getTrustedEmail()
    {
        return trustedEmail;
    }

    /**
     * Returns employeeId.
     *
     * @return the value
     */
    public @Nullable String getEmployeeId()
    {
        return employeeId;
    }
}
