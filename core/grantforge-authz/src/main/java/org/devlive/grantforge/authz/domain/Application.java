// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.authz.AuthorizationChangeListener;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Something whose resources are authorized, such as the GrantForge console itself. The catalog is shared by all
 * tenants (D-40): resources describe the software, roles and grants (per tenant) refer to them. Changes raise the
 * catalog version like resource changes do, so caches keyed by it, such as the console's ID, follow them (D-86).
 */
@Entity
@Table(name = "gf_application")
@EntityListeners(AuthorizationChangeListener.class)
public class Application
        extends BaseEntity
{
    /** The code of the console's own application, registered at start-up. */
    public static final String CONSOLE = "grantforge-console";

    /** Longest name. */
    public static final int NAME_MAX = 128;

    /** Longest description. */
    public static final int DESCRIPTION_MAX = 500;

    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    @Column(name = "code", nullable = false, length = 64)
    private String code = "";

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name = "";

    @Column(name = "description", length = DESCRIPTION_MAX)
    private @Nullable String description;

    @Column(name = "builtin", nullable = false)
    private boolean builtin;

    /** For JPA. */
    protected Application()
    {
    }

    /**
     * Creates an application.
     *
     * @param code identifier: a lowercase letter, then lowercase letters, digits, dots, hyphens or underscores
     * @param name display name
     * @param description optional explanation
     * @return the application
     * @throws IllegalArgumentException if a value is invalid
     */
    public static Application create(String code, String name, @Nullable String description)
    {
        Application application = new Application();
        String normalized = Strings.requireNonBlank(code, "code").toLowerCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code must be 1-64 lowercase letters, digits, '.', '-' or '_' starting"
                    + " with a letter");
        }
        application.code = normalized;
        application.describe(name, description);
        return application;
    }

    /**
     * Changes the name and description.
     *
     * @param newName the name, 1-{@value #NAME_MAX} characters after trimming
     * @param newDescription the description, at most {@value #DESCRIPTION_MAX} characters; blank means none
     * @throws IllegalArgumentException if a value is invalid
     */
    public final void describe(String newName, @Nullable String newDescription)
    {
        name = CatalogText.name(newName, NAME_MAX);
        description = CatalogText.optional(newDescription, DESCRIPTION_MAX, "description");
    }

    /**
     * Marks the application as part of GrantForge, so it cannot be deleted.
     *
     * @return this application
     */
    public Application markBuiltin()
    {
        builtin = true;
        return this;
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
     * Returns whether the application is part of GrantForge.
     *
     * @return the flag
     */
    public boolean isBuiltin()
    {
        return builtin;
    }
}
