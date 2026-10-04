// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Links of accounts to identity sources. */
public interface ExternalIdentityRepository
        extends JpaRepository<ExternalIdentity, Long>
{
    /**
     * Finds the link of an account.
     *
     * @param accountId the account
     * @return the link, if the account is one of a source
     */
    Optional<ExternalIdentity> findByAccountId(long accountId);

    /**
     * Finds the account a source knows by an ID.
     *
     * @param sourceId the source
     * @param externalId what the source calls the user
     * @return the link, if any
     */
    Optional<ExternalIdentity> findBySourceIdAndExternalId(long sourceId, String externalId);

    /**
     * Lists the links of a source.
     *
     * @param sourceId the source
     * @return the links
     */
    List<ExternalIdentity> findBySourceId(long sourceId);

    /**
     * Counts the accounts of a source.
     *
     * @param sourceId the source
     * @return how many
     */
    long countBySourceId(long sourceId);
}
