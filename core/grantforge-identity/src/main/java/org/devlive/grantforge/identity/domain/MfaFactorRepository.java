// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Authenticators of the bound tenant's accounts. */
public interface MfaFactorRepository
        extends JpaRepository<MfaFactor, Long>
{
    /**
     * Finds an account's authenticator.
     *
     * @param accountId the account
     * @return the authenticator, confirmed or not
     */
    Optional<MfaFactor> findByAccountId(long accountId);
}
