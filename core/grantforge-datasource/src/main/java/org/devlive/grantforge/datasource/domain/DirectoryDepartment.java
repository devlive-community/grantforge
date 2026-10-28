// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;

/**
 * DirectoryDepartment owned by one tenant's organization data source.
 */
@Entity
@Table(name = "gf_directory_department")
public class DirectoryDepartment extends TenantScopedEntity
{
    @Column(name = "source_id", nullable = false, updatable = false)
    private long sourceId;

    @Column(name = "external_id", nullable = false, updatable = false, length = 255)
    private String externalId = "";

    @Column(name = "org_unit_id", nullable = false, updatable = false)
    private long orgUnitId;

    /**
     * For JPA.
     */
    protected DirectoryDepartment()
    {
    }

    /**
     * Creates an immutable mapping to a local department.
     *
     * @param source   the data source
     * @param external the provider's identifier
     * @param local    the existing organization ID
     * @return the mapping
     */
    public static DirectoryDepartment of(long source, String external, long local)
    {
        DirectoryDepartment department = new DirectoryDepartment();
        department.sourceId = source;
        department.externalId = external;
        department.orgUnitId = local;
        return department;
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
     * Returns externalId.
     *
     * @return the value
     */
    public String getExternalId()
    {
        return externalId;
    }

    /**
     * Returns orgUnitId.
     *
     * @return the value
     */
    public long getOrgUnitId()
    {
        return orgUnitId;
    }
}
