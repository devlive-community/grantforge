// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.violations;

import jakarta.persistence.EntityManager;

/** Violates NO_NATIVE_SQL through the JPA API. */
public class NativeQueryFixture
{
    Object run(EntityManager entityManager)
    {
        return entityManager.createNativeQuery("select 1").getSingleResult();
    }
}
