// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.authz.AuthorizationChangeListener;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/** A named set of grants of a tenant, given to accounts, groups, departments or positions. */
@Entity
@EntityListeners(AuthorizationChangeListener.class)
@Table(name = "gf_role")
public class Role
        extends TenantScopedEntity
{
    /** Longest name. */
    public static final int NAME_MAX = 128;

    /** Longest description. */
    public static final int DESCRIPTION_MAX = 500;

    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    @Column(name = "code", nullable = false, length = 64)
    private String code = "";

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name = "";

    @Column(name = "description", length = DESCRIPTION_MAX)
    private @Nullable String description;

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role_type", nullable = false, length = 16, updatable = false)
    private RoleType type = RoleType.CUSTOM;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** For JPA. */
    protected Role()
    {
    }

    /**
     * Creates an enabled custom role.
     *
     * @param code identifier unique in the tenant: lowercase letters, digits, dots, hyphens or underscores
     * @param name display name
     * @param description optional explanation
     * @return the role
     * @throws IllegalArgumentException if a value is invalid
     */
    public static Role create(String code, String name, @Nullable String description)
    {
        Role role = new Role();
        role.change(code, name, description);
        return role;
    }

    /**
     * Creates a system role.
     *
     * @param definition which one
     * @return the role
     */
    public static Role system(SystemRole definition)
    {
        Role role = create(definition.code(), definition.defaultName(), null);
        role.type = RoleType.SYSTEM;
        return role;
    }

    /**
     * Changes the code, name and description.
     *
     * @param newCode the code, as for {@link #create}
     * @param newName the name, 1-{@value #NAME_MAX} characters after trimming
     * @param newDescription the description, at most {@value #DESCRIPTION_MAX} characters; blank means none
     * @throws IllegalArgumentException if a value is invalid
     * @throws IllegalStateException for a system role
     */
    public final void change(String newCode, String newName, @Nullable String newDescription)
    {
        requireCustom();
        String normalized = Strings.requireNonBlank(newCode, "code").toLowerCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code must be 1-64 lowercase letters, digits, '.', '-' or '_'");
        }
        String trimmed = Strings.requireNonBlank(newName, "name");
        if (trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException("name must be at most " + NAME_MAX + " characters");
        }
        String explanation = Strings.blankToNull(newDescription);
        if (explanation != null && explanation.length() > DESCRIPTION_MAX) {
            throw new IllegalArgumentException("description must be at most " + DESCRIPTION_MAX + " characters");
        }
        code = normalized;
        name = trimmed;
        description = explanation;
    }

    /**
     * Enables or disables the role; a disabled role grants nothing.
     *
     * @param value whether it is enabled
     * @throws IllegalStateException for a system role
     */
    public void enable(boolean value)
    {
        requireCustom();
        enabled = value;
    }

    private void requireCustom()
    {
        if (type == RoleType.SYSTEM) {
            throw new IllegalStateException("system role " + code + " cannot be changed");
        }
    }

    /**
     * Returns the code.
     *
     * @return the lowercase code
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
     * Returns where the role comes from.
     *
     * @return the type
     */
    public RoleType getType()
    {
        return requireNonNull(type, "type");
    }

    /**
     * Returns whether the role grants anything.
     *
     * @return {@code false} once disabled
     */
    public boolean isEnabled()
    {
        return enabled;
    }
}
