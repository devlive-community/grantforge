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
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A separation-of-duties constraint (D-73): of its roles (see {@link SodConstraintRole}) nobody may hold more than
 * {@link #getMaxRoles()}, however they hold them: directly, through a group, department or position, or by inheritance.
 */
@Entity
@Table(name = "gf_sod_constraint")
public class SodConstraint
        extends TenantScopedEntity
{
    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code = "";

    @Column(name = "name", nullable = false, length = 128)
    private String name = "";

    @Column(name = "description", length = 512)
    private @Nullable String description;

    @Column(name = "max_roles", nullable = false)
    private int maxRoles = 1;

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "enforcement", nullable = false, length = 16)
    private SodMode mode = SodMode.ENFORCE;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** For JPA. */
    protected SodConstraint()
    {
    }

    /**
     * Creates an enabled constraint.
     *
     * @param code the code, unique in the tenant
     * @return the constraint
     */
    public static SodConstraint create(String code)
    {
        SodConstraint constraint = new SodConstraint();
        constraint.code = requireNonNull(code, "code");
        return constraint;
    }

    /**
     * Changes what can change.
     *
     * @param newName the name
     * @param newDescription a longer explanation, or {@code null}
     * @param newMaxRoles how many of the roles one account may hold, at least 1
     * @param newMode what a conflict does
     * @param on whether the constraint applies
     * @throws IllegalArgumentException if fewer than one role may be held
     */
    public void configure(String newName, @Nullable String newDescription, int newMaxRoles, SodMode newMode, boolean on)
    {
        if (newMaxRoles < 1) {
            throw new IllegalArgumentException("max roles must be at least 1");
        }
        this.name = requireNonNull(newName, "name");
        this.description = newDescription;
        this.maxRoles = newMaxRoles;
        this.mode = requireNonNull(newMode, "mode");
        this.enabled = on;
    }

    /**
     * Returns the code.
     *
     * @return the code
     */
    public String getCode()
    {
        return code;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns the description.
     *
     * @return the description, or {@code null}
     */
    public @Nullable String getDescription()
    {
        return description;
    }

    /**
     * Returns how many of the roles one account may hold.
     *
     * @return at least 1
     */
    public int getMaxRoles()
    {
        return maxRoles;
    }

    /**
     * Returns what a conflict does.
     *
     * @return the mode
     */
    public SodMode getMode()
    {
        return mode;
    }

    /**
     * Returns whether the constraint applies.
     *
     * @return the flag
     */
    public boolean isEnabled()
    {
        return enabled;
    }
}
