// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A checked {@link SecuredEntity} declaration.
 *
 * @param code the entity's code in permissions
 * @param name what its rows are
 * @param type the entity class
 * @param fields the fields conditions may test, in declaration order
 * @param owner the attribute holding the owning account, or {@code null}
 * @param unit the attribute holding the owning department, or {@code null}
 * @param unitFromOwner whether rows belong to their owner's departments
 * @param tenantScoped whether Hibernate keeps the entity to the bound tenant by itself
 * @param tenant the attribute holding the tenant of an entity that is not tenant-scoped, or {@code null}
 */
public record SecuredEntityDefinition(String code, String name, Class<?> type, List<DataField> fields, @Nullable String owner,
        @Nullable String unit, boolean unitFromOwner, boolean tenantScoped, @Nullable String tenant)
{
    /** The prefix of the catalog resources of entities. */
    public static final String RESOURCE_PREFIX = "entity:";

    /** Checks and copies the values. */
    public SecuredEntityDefinition
    {
        requireNonNull(code, "code");
        requireNonNull(name, "name");
        requireNonNull(type, "type");
        fields = List.copyOf(fields);
    }

    /**
     * Returns the code of the entity's catalog resource.
     *
     * @return {@code entity:} followed by the code
     */
    public String resourceCode()
    {
        return RESOURCE_PREFIX + code;
    }

    /**
     * Returns whether rows belong to departments, directly or through their owner.
     *
     * @return {@code true} if department scopes apply
     */
    public boolean hasUnits()
    {
        return unit != null || unitFromOwner;
    }

    /**
     * Returns the scopes data permissions may use on the entity: the tenant always (and every tenant, for platform
     * administration), departments if rows belong to some, own rows if rows have an owner, conditions if any field is
     * filterable.
     *
     * @return the scopes
     */
    public Set<DataScope> scopes()
    {
        Set<DataScope> scopes = EnumSet.of(DataScope.ALL, DataScope.TENANT);
        if (hasUnits()) {
            scopes.addAll(EnumSet.of(DataScope.ORG_AND_CHILDREN, DataScope.ORG, DataScope.CUSTOM_ORGS));
        }
        if (owner != null) {
            scopes.add(DataScope.SELF);
        }
        if (!fields.isEmpty()) {
            scopes.add(DataScope.CONDITION);
        }
        return scopes;
    }

    /**
     * Finds a filterable field.
     *
     * @param fieldCode the attribute
     * @return the field, or empty if conditions may not test it
     */
    public Optional<DataField> field(String fieldCode)
    {
        return fields.stream().filter(field -> field.code().equals(fieldCode)).findFirst();
    }
}
