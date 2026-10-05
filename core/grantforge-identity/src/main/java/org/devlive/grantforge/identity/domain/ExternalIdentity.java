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

/**
 * Makes an account one of an identity source, whose directory or provider then signs it in (D-72); the password kept
 * for the account is not used. An account belongs to at most one source.
 */
@Entity
@Table(name = "gf_external_identity")
public class ExternalIdentity
        extends TenantScopedEntity
{
    @Column(name = "account_id", nullable = false, updatable = false)
    private long accountId;

    @Column(name = "source_id", nullable = false, updatable = false)
    private long sourceId;

    @Column(name = "external_id", nullable = false, updatable = false, length = 255)
    private String externalId = "";

    /** For JPA. */
    protected ExternalIdentity()
    {
    }

    /**
     * Links an account.
     *
     * @param accountId the account
     * @param sourceId the source
     * @param externalId what the source calls the user, stable across renames
     * @return the link
     */
    public static ExternalIdentity of(long accountId, long sourceId, String externalId)
    {
        ExternalIdentity link = new ExternalIdentity();
        link.accountId = accountId;
        link.sourceId = sourceId;
        link.externalId = Strings.requireNonBlank(externalId, "externalId");
        return link;
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
     * Returns the source.
     *
     * @return its ID
     */
    public long getSourceId()
    {
        return sourceId;
    }

    /**
     * Returns what the source calls the user.
     *
     * @return the ID
     */
    public String getExternalId()
    {
        return externalId;
    }
}
