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

/**
 * An account's authenticator app (TOTP). Its secret is sealed; it signs the account in only once confirmed, and remembers
 * the last time step it accepted so no code works twice.
 */
@Entity
@Table(name = "gf_mfa_factor")
public class MfaFactor
        extends TenantScopedEntity
{
    @Column(name = "account_id", nullable = false, updatable = false)
    private long accountId;

    @Column(name = "secret", nullable = false, length = 255)
    private String secret = "";

    @Column(name = "confirmed_at")
    private @Nullable Instant confirmedAt;

    @Column(name = "last_step")
    private @Nullable Long lastStep;

    /** For JPA. */
    protected MfaFactor()
    {
    }

    /**
     * Starts enrolling an authenticator.
     *
     * @param accountId the account
     * @param sealedSecret the secret, sealed
     * @return the factor, not confirmed yet
     */
    public static MfaFactor enroll(long accountId, String sealedSecret)
    {
        MfaFactor factor = new MfaFactor();
        factor.accountId = accountId;
        factor.secret = requireNonNull(sealedSecret, "sealedSecret");
        return factor;
    }

    /**
     * Restarts enrolment with a new secret, as when the user scans again before confirming.
     *
     * @param sealedSecret the new secret, sealed
     */
    // NULL is how the nullable columns say "not confirmed, no step used yet".
    @SuppressWarnings("PMD.NullAssignment")
    public void reenroll(String sealedSecret)
    {
        this.secret = requireNonNull(sealedSecret, "sealedSecret");
        this.confirmedAt = null;
        this.lastStep = null;
    }

    /**
     * Confirms the authenticator, once the user entered a code from it.
     *
     * @param now the current time
     * @param step the time step of that code, used up
     */
    public void confirm(Instant now, long step)
    {
        this.confirmedAt = requireNonNull(now, "now");
        this.lastStep = step;
    }

    /**
     * Uses up a time step; an earlier or the same step fails.
     *
     * @param step the step of the code presented
     * @return whether the step was not used yet
     */
    public boolean use(long step)
    {
        Long last = lastStep;
        if (last != null && step <= last) {
            return false;
        }
        lastStep = step;
        return true;
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
     * Returns the sealed secret.
     *
     * @return the secret
     */
    public String getSecret()
    {
        return secret;
    }

    /**
     * Returns whether the authenticator was confirmed.
     *
     * @return {@code true} once it signs the account in
     */
    public boolean isConfirmed()
    {
        return confirmedAt != null;
    }

    /**
     * Returns when it was confirmed.
     *
     * @return the time, or {@code null}
     */
    public @Nullable Instant getConfirmedAt()
    {
        return confirmedAt;
    }
}
