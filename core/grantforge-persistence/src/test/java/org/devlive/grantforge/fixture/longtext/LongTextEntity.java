// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.fixture.longtext;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * An entity with long text, mapped as the services module maps its JSON; outside the packages the persistence tests
 * scan, so only the tests that name it map it.
 */
@Entity
@Table(name = "gf_long_text_fixture")
public class LongTextEntity
{
    @Id
    private @Nullable Long id;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "body", length = Length.LONG32)
    private @Nullable String body;

    /**
     * Returns the text.
     *
     * @return the text, if any
     */
    public @Nullable String getBody()
    {
        return body;
    }

    /**
     * Returns the ID.
     *
     * @return the ID, if any
     */
    public @Nullable Long getId()
    {
        return id;
    }
}
