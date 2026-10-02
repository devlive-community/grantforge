// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.devlive.grantforge.persistence.tenant.TenantSampleEntity;

/** Samples through a scoped repository. */
public interface ScopedSamples
        extends ScopedRepository<TenantSampleEntity, Long>
{
}
