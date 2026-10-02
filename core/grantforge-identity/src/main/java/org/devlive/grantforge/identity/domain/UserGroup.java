// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.authz.AuthorizationChangeListener;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/** A named set of accounts of one tenant, granted permissions together once roles exist. */
@Entity
@EntityListeners(AuthorizationChangeListener.class)
@Table(name = "gf_user_group")
public class UserGroup
        extends TenantScopedEntity
{
    /** Longest name. */
    public static final int NAME_MAX = 128;

    /** Longest description. */
    public static final int DESCRIPTION_MAX = 512;

    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    @Column(name = "code", nullable = false, length = 64)
    private String code = "";

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name = "";

    @Column(name = "description", length = DESCRIPTION_MAX)
    private @Nullable String description;

    /** For JPA. */
    protected UserGroup()
    {
    }

    /**
     * Creates a group.
     *
     * @param code identifier unique in the tenant: lowercase letters, digits, dots, hyphens or underscores
     * @param name display name
     * @param description what the group is for, if anything
     * @return the group
     * @throws IllegalArgumentException if a value is invalid
     */
    public static UserGroup create(String code, String name, @Nullable String description)
    {
        UserGroup group = new UserGroup();
        group.change(code, name, description);
        return group;
    }

    /**
     * Changes the code, name and description.
     *
     * @param newCode the code, as for {@link #create}
     * @param newName the name, 1-{@value #NAME_MAX} characters after trimming
     * @param newDescription the description, at most {@value #DESCRIPTION_MAX} characters; blank clears it
     * @throws IllegalArgumentException if a value is invalid
     */
    public final void change(String newCode, String newName, @Nullable String newDescription)
    {
        String normalized = Strings.requireNonBlank(newCode, "code").toLowerCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code must be 1-64 lowercase letters, digits, '.', '-' or '_'");
        }
        String trimmed = Strings.requireNonBlank(newName, "name");
        if (trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException("name must be at most " + NAME_MAX + " characters");
        }
        String text = Strings.blankToNull(newDescription);
        if (text != null && text.length() > DESCRIPTION_MAX) {
            throw new IllegalArgumentException("description must be at most " + DESCRIPTION_MAX + " characters");
        }
        code = normalized;
        name = trimmed;
        description = text;
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
}
