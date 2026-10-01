// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence of {@link ConsoleSession}s; queries are filtered to the bound tenant. */
public interface ConsoleSessionRepository
        extends JpaRepository<ConsoleSession, Long>
{
    /**
     * Finds the entry of a session.
     *
     * @param sessionId Spring Session's ID
     * @return the entry, if any
     */
    Optional<ConsoleSession> findBySessionId(String sessionId);

    /**
     * Returns an account's sessions active since a time, most recently used first.
     *
     * @param accountId the account
     * @param since sessions last seen before this time are expired
     * @return the entries
     */
    List<ConsoleSession> findByAccountIdAndLastSeenAtAfterOrderByLastSeenAtDescIdDesc(long accountId, Instant since);

    /**
     * Returns every entry of an account, expired ones included.
     *
     * @param accountId the account
     * @return the entries
     */
    List<ConsoleSession> findByAccountId(long accountId);

    /**
     * Returns the tenant's sessions active since a time with their owners, most recently used first.
     *
     * @param since sessions last seen before this time are expired
     * @param page the page
     * @return the entries
     */
    @Query(value = "select new org.devlive.grantforge.identity.domain.ConsoleSessionEntry(s, a.username, a.displayName)"
            + " from ConsoleSession s join UserAccount a on a.id = s.accountId"
            + " where s.lastSeenAt > :since order by s.lastSeenAt desc, s.id desc",
            countQuery = "select count(s) from ConsoleSession s where s.lastSeenAt > :since")
    Page<ConsoleSessionEntry> findActive(@Param("since") Instant since, Pageable page);

    /**
     * Records activity of a session without loading it (so concurrent requests cannot conflict on its version).
     *
     * @param sessionId Spring Session's ID
     * @param now the activity time
     * @return the number of updated entries, 0 if the session is not indexed
     */
    @Modifying
    @Query("update ConsoleSession s set s.lastSeenAt = :now where s.sessionId = :sessionId")
    int touch(@Param("sessionId") String sessionId, @Param("now") Instant now);

    /**
     * Removes the entry of a session.
     *
     * @param sessionId Spring Session's ID
     * @return the number of removed entries
     */
    @Modifying
    @Query("delete from ConsoleSession s where s.sessionId = :sessionId")
    int deleteBySessionId(@Param("sessionId") String sessionId);

    /**
     * Removes entries of sessions that expired.
     *
     * @param before entries last seen before this time are removed
     * @return the number of removed entries
     */
    @Modifying
    @Query("delete from ConsoleSession s where s.lastSeenAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
