// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.secured.FilterableField;
import org.devlive.grantforge.persistence.secured.SecuredEntity;

/** Minimal tenant-scoped entity used to exercise tenant isolation against a real database. */
@Entity
@Table(name = "gf_tenant_sample")
@SecuredEntity(code = "tenant-sample", name = "Samples")
public class TenantSampleEntity
        extends TenantScopedEntity
{
    @FilterableField("Label")
    @Column(name = "label", nullable = false, length = 64)
    private String label = "";

    protected TenantSampleEntity()
    {
    }

    public TenantSampleEntity(String label)
    {
        this.label = label;
    }

    public String getLabel()
    {
        return label;
    }
}
