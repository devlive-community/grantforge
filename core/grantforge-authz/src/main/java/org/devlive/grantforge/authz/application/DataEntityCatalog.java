// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;

import static java.util.Objects.requireNonNull;

/**
 * Keeps a catalog resource for every secured entity, {@code entity:} followed by its code, below the built-in
 * module {@value #MODULE}, so data permissions can refer to entities the way the rest of the catalog refers to pages and APIs.
 * Entities the code no longer declares keep their resource, as permissions may still refer to it.
 */
@Service
public final class DataEntityCatalog
{
    /** The code of the built-in module the entities sit in. */
    public static final String MODULE = "entities";

    private final ResourceRepository resources;
    private final ApplicationService applications;
    private final TransactionTemplate transactions;

    /**
     * Creates the catalog.
     *
     * @param resources the resources
     * @param applications the applications, for the console's own
     * @param transactionManager opens transactions
     */
    public DataEntityCatalog(ResourceRepository resources, ApplicationService applications, PlatformTransactionManager transactionManager)
    {
        this.resources = requireNonNull(resources, "resources");
        this.applications = requireNonNull(applications, "applications");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Brings the catalog in step with the secured entities.
     *
     * @param entities every entity the code declares
     * @return how many resources were created
     * @throws IllegalStateException if a resource with an entity's code exists but is no data entity
     */
    public int synchronize(Collection<SecuredEntityDefinition> entities)
    {
        long console = applications.registerConsole();
        return requireNonNull(transactions.execute(status -> {
            Resource module = module(console);
            int created = 0;
            for (SecuredEntityDefinition entity : entities) {
                Resource resource = resources.findByApplicationIdAndCode(console, entity.resourceCode()).orElse(null);
                if (resource == null) {
                    resources.save(create(console, module, entity));
                    created++;
                }
                else if (resource.getType() != ResourceType.DATA_ENTITY) {
                    throw new IllegalStateException("resource " + resource.getCode() + " exists but is no data entity");
                }
                else {
                    resource.declare(entity.name(), resource.getNameKey(), null);
                    resource.markBuiltin();
                }
            }
            return created;
        }));
    }

    private Resource create(long console, Resource module, SecuredEntityDefinition entity)
    {
        return Resource.create(console, module, ResourceType.DATA_ENTITY, entity.resourceCode(), new ResourceDetails(entity.name(), null,
                null, true, true, DenyMode.HIDE), resources.findChildren(console, module.requireId()).size()).markBuiltin();
    }

    private Resource module(long console)
    {
        return resources.findByApplicationIdAndCode(console, MODULE).orElseGet(() -> resources.save(Resource.create(console, null,
                ResourceType.MODULE, MODULE, new ResourceDetails("Data entities", "Entities whose rows data permissions restrict", null,
                        true, true, DenyMode.HIDE), resources.findChildren(console, null).size()).markBuiltin()));
    }
}
