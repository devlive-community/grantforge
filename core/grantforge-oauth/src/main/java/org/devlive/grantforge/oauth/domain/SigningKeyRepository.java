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
import java.util.List;

/** Signing keys of the authorization server. */
public interface SigningKeyRepository
        extends JpaRepository<SigningKeyRecord, Long>
{
    /**
     * Lists the keys, newest first.
     *
     * @return the keys
     */
    List<SigningKeyRecord> findAllByOrderByActivatedAtDescIdDesc();

    /**
     * Deletes keys retired before a time, whose tokens have all expired.
     *
     * @param before the time
     * @return how many went
     */
    @Modifying
    @Query("delete from SigningKeyRecord k where k.retiredAt < :before")
    int deleteRetiredBefore(@Param("before") Instant before);
}
