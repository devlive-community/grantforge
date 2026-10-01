// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** Shared helpers of the catalog tests. */
public final class CatalogTestData
{
    private CatalogTestData()
    {
    }

    /**
     * Returns visible, enabled settings with a name.
     *
     * @param name the name
     * @return the settings
     */
    public static ResourceDetails details(String name)
    {
        return new ResourceDetails(name, null, null, true, true, DenyMode.HIDE);
    }

    /**
     * Deletes every resource, children first: the parent foreign key forbids deleting a parent before its children.
     *
     * @param resources the repository
     * @param transactionManager opens the transaction
     */
    public static void deleteResources(ResourceRepository resources, PlatformTransactionManager transactionManager)
    {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<Resource> all = resources.findAll(Sort.by("depth"));
            for (int i = all.size() - 1; i >= 0; i--) {
                resources.delete(all.get(i));
            }
        });
    }
}
