// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.clean;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/** A JPQL query is portable and allowed anywhere. */
public interface JpqlRepositoryFixture
        extends Repository<CleanEntity, Long>
{
    /**
     * Portable query.
     *
     * @return entities
     */
    @Query("select e from CleanEntity e")
    List<CleanEntity> all();
}
