// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.clean;

import jakarta.persistence.EntityManager;

/** Methods that merely look like native queries, or portable JPQL, are allowed. */
public class ReportBuilder
{
    /**
     * A domain method whose name happens to start with "createNative".
     *
     * @return a label
     */
    public String createNativeReport()
    {
        return "report";
    }

    /**
     * Calls the look-alike method and a portable JPQL query.
     *
     * @param entityManager the entity manager
     * @return the label
     */
    public String build(EntityManager entityManager)
    {
        entityManager.createQuery("select e from CleanEntity e");
        return createNativeReport();
    }
}
