// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence of {@link Tenant}s. */
public interface TenantRepository
        extends JpaRepository<Tenant, Long>
{
}
