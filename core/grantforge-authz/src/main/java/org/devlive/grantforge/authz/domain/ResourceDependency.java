// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * That one resource needs another of the same application, such as a button that calls APIs or needs another
 * button. Only console elements (menus, pages, tabs, buttons) have dependencies, and they depend on APIs, pages,
 * tabs or buttons. Dependencies never form a cycle.
 */
@Entity
@Table(name = "gf_resource_dependency")
public class ResourceDependency
        extends BaseEntity
{
    /** Types that may depend on others. */
    public static final Set<ResourceType> DEPENDENTS = Set.of(ResourceType.MENU, ResourceType.PAGE, ResourceType.TAB,
            ResourceType.ACTION);

    /** Types that may be depended on. */
    public static final Set<ResourceType> TARGETS = Set.of(ResourceType.API, ResourceType.PAGE, ResourceType.TAB,
            ResourceType.ACTION);

    @Column(name = "application_id", nullable = false, updatable = false)
    private long applicationId;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private long resourceId;

    @Column(name = "depends_on_id", nullable = false, updatable = false)
    private long dependsOnId;

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "dependency_kind", nullable = false, length = 16)
    private DependencyKind kind = DependencyKind.REQUIRED;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "dependency_source", nullable = false, length = 16, updatable = false)
    private DependencySource source = DependencySource.MANUAL;

    /** For JPA. */
    protected ResourceDependency()
    {
    }

    /**
     * Creates a dependency. Callers must make sure it does not close a cycle ({@link DependencyGraph}).
     *
     * @param resource the resource that needs the other
     * @param dependsOn the resource it needs
     * @param kind how strongly
     * @param source where the dependency comes from
     * @return the dependency
     * @throws IllegalArgumentException if the resources belong to different applications, are the same, or their
     *         types cannot depend on each other
     */
    public static ResourceDependency create(Resource resource, Resource dependsOn, DependencyKind kind, DependencySource source)
    {
        if (resource.getApplicationId() != dependsOn.getApplicationId()) {
            throw new IllegalArgumentException("dependencies stay within one application");
        }
        if (resource.requireId() == dependsOn.requireId()) {
            throw new IllegalArgumentException("a resource cannot depend on itself");
        }
        if (!DEPENDENTS.contains(resource.getType()) || !TARGETS.contains(dependsOn.getType())) {
            throw new IllegalArgumentException(resource.getType() + " cannot depend on " + dependsOn.getType());
        }
        ResourceDependency dependency = new ResourceDependency();
        dependency.applicationId = resource.getApplicationId();
        dependency.resourceId = resource.requireId();
        dependency.dependsOnId = dependsOn.requireId();
        dependency.kind = requireNonNull(kind, "kind");
        dependency.source = requireNonNull(source, "source");
        return dependency;
    }

    /**
     * Makes the dependency required or optional.
     *
     * @param value the new kind
     */
    public void changeKind(DependencyKind value)
    {
        kind = requireNonNull(value, "value");
    }

    /**
     * Returns the application.
     *
     * @return the application ID
     */
    public long getApplicationId()
    {
        return applicationId;
    }

    /**
     * Returns the resource that needs the other.
     *
     * @return its ID
     */
    public long getResourceId()
    {
        return resourceId;
    }

    /**
     * Returns the resource that is needed.
     *
     * @return its ID
     */
    public long getDependsOnId()
    {
        return dependsOnId;
    }

    /**
     * Returns how strongly the resource needs the other.
     *
     * @return the kind
     */
    public DependencyKind getKind()
    {
        return kind;
    }

    /**
     * Returns where the dependency comes from.
     *
     * @return the source
     */
    public DependencySource getSource()
    {
        return source;
    }
}
