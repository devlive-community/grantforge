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

/** Authorizations of the authorization server. */
public interface StoredAuthorizationRepository
        extends JpaRepository<StoredAuthorization, Long>
{
    /**
     * Finds an authorization by the authorization server's identifier.
     *
     * @param authorizationId the identifier
     * @return the authorization
     */
    Optional<StoredAuthorization> findByAuthorizationId(String authorizationId);

    /**
     * Finds the authorization holding an authorization code.
     *
     * @param hash the code's hash
     * @return the authorization
     */
    Optional<StoredAuthorization> findByCodeHash(String hash);

    /**
     * Finds the authorization holding an access token.
     *
     * @param hash the token's hash
     * @return the authorization
     */
    Optional<StoredAuthorization> findByAccessHash(String hash);

    /**
     * Finds the authorization holding a refresh token.
     *
     * @param hash the token's hash
     * @return the authorization
     */
    Optional<StoredAuthorization> findByRefreshHash(String hash);

    /**
     * Finds the authorization holding an ID token.
     *
     * @param hash the token's hash
     * @return the authorization
     */
    Optional<StoredAuthorization> findByIdTokenHash(String hash);

    /**
     * Deletes the authorizations of a client.
     *
     * @param registeredClientId the client's record
     * @return how many went
     */
    @Modifying
    @Query("delete from StoredAuthorization a where a.registeredClientId = :client")
    int deleteByClient(@Param("client") String registeredClientId);

    /**
     * Deletes the authorizations of an account, as when it is disabled.
     *
     * @param accountId the account
     * @return how many went
     */
    @Modifying
    @Query("delete from StoredAuthorization a where a.accountId = :account")
    int deleteByAccount(@Param("account") long accountId);

    /**
     * Deletes authorizations whose last token has expired.
     *
     * @param now the current time
     * @return how many went
     */
    @Modifying
    @Query("delete from StoredAuthorization a where a.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
