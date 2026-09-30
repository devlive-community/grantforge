// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Minimal entity used to exercise {@link BaseEntity} against a real database. */
@Entity
@Table(name = "gf_sample")
public class SampleEntity
        extends BaseEntity
{
    @Column(name = "label", nullable = false, length = 64)
    private String label = "";

    protected SampleEntity()
    {
    }

    public SampleEntity(String label)
    {
        this.label = label;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }
}
