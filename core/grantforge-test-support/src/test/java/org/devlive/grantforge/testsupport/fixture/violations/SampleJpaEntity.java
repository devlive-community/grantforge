// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.violations;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** An entity that API code must not expose. */
@Entity
public class SampleJpaEntity
{
    @Id
    private Long id = 0L;

    Long id()
    {
        return id;
    }
}
