// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.FieldDirection;
import org.devlive.grantforge.authz.domain.FieldUsage;
import org.devlive.grantforge.authz.domain.FieldUsageRepository;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.secured.DeclaredField;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Keeps a catalog resource for every secured entity, {@code entity:} followed by its code, below the built-in
 * module {@value #MODULE}, so data permissions can refer to entities the way the rest of the catalog refers to pages and APIs.
 * Below each entity sit its secured fields, {@code entity:user.email} for instance, with the APIs they appear in. Entities
 * and fields the code no longer declares keep their resource, as permissions may still refer to them; their APIs go.
 */
@Service
public final class DataEntityCatalog
{
    /** The code of the built-in module the entities sit in. */
    public static final String MODULE = "entities";

    private final ResourceRepository resources;
    private final ApplicationService applications;
    private final TransactionTemplate transactions;
    private final FieldUsageRepository usages;

    /**
     * Creates the catalog.
     *
     * @param resources the resources
     * @param applications the applications, for the console's own
     * @param transactionManager opens transactions
     * @param usages where secured fields appear
     */
    public DataEntityCatalog(ResourceRepository resources, ApplicationService applications, PlatformTransactionManager transactionManager,
            FieldUsageRepository usages)
    {
        this.usages = requireNonNull(usages, "usages");
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

    /**
     * Brings the secured fields and the APIs they appear in in step with the code; runs after the entities are in step.
     *
     * @param appearances every API's return or acceptance of a secured field
     * @return how many field resources were created
     * @throws IllegalStateException if a field belongs to no entity of the catalog, or a resource with a field's code
     *         exists but is no field
     */
    public int synchronizeFields(Collection<FieldAppearance> appearances)
    {
        long console = applications.registerConsole();
        return requireNonNull(transactions.execute(status -> {
            Map<String, DeclaredField> declared = new LinkedHashMap<>();
            appearances.forEach(appearance -> declared.putIfAbsent(appearance.field().resourceCode(), appearance.field()));
            Map<String, Long> ids = new HashMap<>();
            int created = 0;
            for (DeclaredField field : declared.values()) {
                Resource resource = resources.findByApplicationIdAndCode(console, field.resourceCode()).orElse(null);
                if (resource == null) {
                    resource = resources.save(create(console, field));
                    created++;
                }
                else if (resource.getType() != ResourceType.FIELD) {
                    throw new IllegalStateException("resource " + resource.getCode() + " exists but is no field");
                }
                else {
                    resource.declare(field.name(), resource.getNameKey(), null);
                    resource.markBuiltin();
                }
                ids.put(field.resourceCode(), resource.requireId());
            }
            Set<Key> wanted = appearances.stream().map(appearance -> new Key(requireNonNull(ids.get(appearance.field()
                    .resourceCode())), appearance.httpMethod().toUpperCase(Locale.ROOT), appearance.pathPattern(),
                    appearance.direction())).collect(Collectors.toSet());
            List<FieldUsage> existing = usages.findAll();
            usages.deleteAll(existing.stream().filter(usage -> !wanted.contains(Key.of(usage))).toList());
            Set<Key> kept = existing.stream().map(Key::of).collect(Collectors.toSet());
            usages.saveAll(wanted.stream().filter(key -> !kept.contains(key)).sorted(Comparator.comparing(Key::pathPattern)
                    .thenComparing(Key::httpMethod)).map(key -> FieldUsage.of(resources.getReferenceById(key.resourceId()),
                    key.httpMethod(), key.pathPattern(), key.direction())).toList());
            return created;
        }));
    }

    /**
     * Returns the APIs a secured field appears in.
     *
     * @param resourceId the field's resource
     * @return where it appears, by path and method
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} if the resource is no field
     */
    public List<FieldUsageView> usages(long resourceId)
    {
        return requireNonNull(transactions.execute(status -> {
            if (resources.findById(resourceId).filter(resource -> resource.getType() == ResourceType.FIELD).isEmpty()) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no field " + resourceId);
            }
            return usages.findByResourceIdOrderByPathPatternAscHttpMethodAscDirectionAsc(resourceId).stream()
                    .map(FieldUsageView::from).toList();
        }));
    }

    private Resource create(long console, DeclaredField field)
    {
        Resource entity = resources.findByApplicationIdAndCode(console, SecuredEntityDefinition.RESOURCE_PREFIX + field.entity())
                .filter(resource -> resource.getType() == ResourceType.DATA_ENTITY)
                .orElseThrow(() -> new IllegalStateException("secured field " + field.resourceCode() + " belongs to no entity"));
        return Resource.create(console, entity, ResourceType.FIELD, field.resourceCode(), new ResourceDetails(field.name(), null,
                null, true, true, DenyMode.HIDE), resources.findChildren(console, entity.requireId()).size()).markBuiltin();
    }

    private record Key(long resourceId, String httpMethod, String pathPattern, FieldDirection direction)
    {
        static Key of(FieldUsage usage)
        {
            return new Key(usage.getResourceId(), usage.getHttpMethod(), usage.getPathPattern(), usage.getDirection());
        }
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
