// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.clean.persistence.dialect;

import jakarta.persistence.EntityManager;

/** Native SQL is allowed in dialect packages. */
public class DialectFixture
{
    /**
     * Runs a native query.
     *
     * @param entityManager the entity manager
     * @return the result
     */
    public Object run(EntityManager entityManager)
    {
        return entityManager.createNativeQuery("select 1").getSingleResult();
    }
}
