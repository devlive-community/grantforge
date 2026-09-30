// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.violations;

import org.hibernate.Session;

/** Violates NO_NATIVE_SQL through the Hibernate API. */
public class HibernateNativeQueryFixture
{
    Object run(Session session)
    {
        return session.createNativeQuery("select 1", Object.class).getSingleResult();
    }
}
