// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link TenantSampleEntity}. */
public interface TenantSampleRepository
        extends JpaRepository<TenantSampleEntity, Long>
{
}
