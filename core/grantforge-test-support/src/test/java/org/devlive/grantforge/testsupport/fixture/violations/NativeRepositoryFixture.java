// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.violations;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/** Violates NO_NATIVE_SQL through a Spring Data native query. */
public interface NativeRepositoryFixture
        extends Repository<SampleJpaEntity, Long>
{
    /**
     * Native query.
     *
     * @return rows
     */
    @Query(value = "select 1", nativeQuery = true)
    List<Object> raw();
}
