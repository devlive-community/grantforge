// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.clean;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** An entity used only behind the API. */
@Entity
public class CleanEntity
{
    @Id
    private Long id = 0L;

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    public Long id()
    {
        return id;
    }
}
