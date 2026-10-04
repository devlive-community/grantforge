// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** Refresh tokens replaced by rotations. */
public interface RetiredTokenRepository
        extends JpaRepository<RetiredToken, Long>
{
    /**
     * Finds a retired token.
     *
     * @param tokenHash the token's hash
     * @return the record
     */
    Optional<RetiredToken> findByTokenHash(String tokenHash);

    /**
     * Forgets the retired tokens of an authorization, once it is revoked.
     *
     * @param authorizationId the authorization server's identifier
     * @return how many went
     */
    @Modifying
    @Query("delete from RetiredToken t where t.authorizationId = :authorization")
    int deleteByAuthorization(@Param("authorization") String authorizationId);

    /**
     * Forgets tokens that would have expired by now.
     *
     * @param now the current time
     * @return how many went
     */
    @Modifying
    @Query("delete from RetiredToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
