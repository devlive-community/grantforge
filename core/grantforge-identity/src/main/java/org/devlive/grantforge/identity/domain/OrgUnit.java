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
import org.devlive.grantforge.persistence.secured.FilterableField;
import org.devlive.grantforge.persistence.secured.SecuredEntity;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * A department of a tenant's organization tree.
 *
 * <p>The tree is stored as a materialized path (D-35): {@code path} lists the IDs from the root down to this unit,
 * such as {@code /12/34/56/}, so "this unit and everything below it" is the prefix query
 * {@code path LIKE '/12/34/%'} and moving a subtree rewrites the prefix of its paths in one statement.
 */
@Entity
@EntityListeners(AuthorizationChangeListener.class)
@Table(name = "gf_org_unit")
@SecuredEntity(code = "org-unit", name = "Departments", unit = "id")
public class OrgUnit
        extends TenantScopedEntity
{
    /** Deepest allowed level; roots are at depth 0. Keeps paths within their column. */
    public static final int MAX_DEPTH = 15;

    /** Longest name. */
    public static final int NAME_MAX = 128;

    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    @Column(name = "parent_id")
    private @Nullable Long parentId;

    @FilterableField("Code")
    @Column(name = "code", nullable = false, length = 64)
    private String code = "";

    @FilterableField("Name")
    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name = "";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "path", nullable = false, length = 400)
    private String path = "";

    @FilterableField("Depth")
    @Column(name = "depth", nullable = false)
    private int depth;

    /** For JPA. */
    protected OrgUnit()
    {
    }

    /**
     * Creates a unit below a parent, or a root.
     *
     * @param parent the parent, or {@code null} for a root
     * @param code identifier unique in the tenant: lowercase letters, digits, dots, hyphens or underscores
     * @param name display name
     * @param sortOrder position among its siblings
     * @return the unit, with its ID already assigned
     * @throws IllegalArgumentException if a value is invalid or the parent is at the maximum depth
     */
    public static OrgUnit create(@Nullable OrgUnit parent, String code, String name, int sortOrder)
    {
        OrgUnit unit = new OrgUnit();
        long id = unit.preassignId();
        if (parent != null) {
            unit.parentId = parent.requireId();
            unit.depth = parent.depth + 1;
        }
        if (unit.depth > MAX_DEPTH) {
            throw new IllegalArgumentException("organizations nest at most " + (MAX_DEPTH + 1) + " levels");
        }
        unit.path = (parent == null ? "/" : parent.path) + id + "/";
        unit.rename(code, name);
        unit.sortOrder = sortOrder;
        return unit;
    }

    /**
     * Changes the code and name.
     *
     * @param newCode the code, as for {@link #create}
     * @param newName the name, 1-{@value #NAME_MAX} characters after trimming
     * @throws IllegalArgumentException if a value is invalid
     */
    public final void rename(String newCode, String newName)
    {
        String normalized = Strings.requireNonBlank(newCode, "code").toLowerCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code must be 1-64 lowercase letters, digits, '.', '-' or '_'");
        }
        String trimmed = Strings.requireNonBlank(newName, "name");
        if (trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException("name must be at most " + NAME_MAX + " characters");
        }
        code = normalized;
        name = trimmed;
    }

    /**
     * Returns whether a unit lies in this unit's subtree (itself included).
     *
     * @param other the other unit
     * @return {@code true} if {@code other} is this unit or below it
     */
    public boolean contains(OrgUnit other)
    {
        return other.path.startsWith(path);
    }

    /**
     * Sets the position among siblings.
     *
     * @param value the position
     */
    public void placeAt(int value)
    {
        sortOrder = value;
    }

    /**
     * Returns the parent.
     *
     * @return the parent ID, or {@code null} for a root
     */
    public @Nullable Long getParentId()
    {
        return parentId;
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
     * Returns the position among siblings.
     *
     * @return the sort order
     */
    public int getSortOrder()
    {
        return sortOrder;
    }

    /**
     * Returns the IDs from the root down to this unit, as {@code /root/.../this/}.
     *
     * @return the path
     */
    public String getPath()
    {
        return requireNonNull(path, "path");
    }

    /**
     * Returns the level; roots are at 0.
     *
     * @return the depth
     */
    public int getDepth()
    {
        return depth;
    }
}
