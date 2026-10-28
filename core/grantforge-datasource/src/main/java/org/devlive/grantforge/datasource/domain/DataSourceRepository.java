// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-filtered data source configurations.
 */
public interface DataSourceRepository extends JpaRepository<DataSource, Long>
{
    /**
     * Lists configured sources.
     *
     * @return the sources
     */
    List<DataSource> findAllByOrderByIdAsc();

    /**
     * Finds the organization authority for an OIDC source.
     *
     * @param identitySourceId the OIDC source
     * @return the data source
     */
    Optional<DataSource> findByIdentitySourceId(long identitySourceId);

    /**
     * Locks the source for fencing configuration, jobs and login updates.
     *
     * @param id the source
     * @return the locked source
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DataSource s where s.id = :id")
    Optional<DataSource> lockById(@Param("id") long id);
}
