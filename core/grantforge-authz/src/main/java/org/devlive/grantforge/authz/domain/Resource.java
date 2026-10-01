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
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * A menu, page, button, API, data entity or field of an application. Resources form a tree per application,
 * stored as a materialized path like the organization tree (D-35): {@code path} lists the IDs from the top down to
 * this resource, so a subtree is a prefix query and moving one rewrites the prefix.
 */
@Entity
@Table(name = "gf_resource")
public class Resource
        extends BaseEntity
{
    /** Deepest allowed level; top-level resources are at depth 0. Keeps paths within their column. */
    public static final int MAX_DEPTH = 15;

    /** Longest name. */
    public static final int NAME_MAX = 128;

    /** Longest description. */
    public static final int DESCRIPTION_MAX = 500;

    /** Longest code and route. */
    public static final int CODE_MAX = 255;

    /**
     * Resource codes: letters, digits and {@code . _ : / { } * @ -}, so both {@code system.user.list} and API codes
     * such as {@code api:GET:/api/v1/users/{id}} fit. Codes are case-sensitive.
     */
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/{}*@-]{0,254}");

    private static final Pattern ROUTE = Pattern.compile("/[^\\s]{0,254}");

    private static final Set<ResourceType> ROUTED = Set.of(ResourceType.MENU, ResourceType.PAGE, ResourceType.TAB);

    @Column(name = "application_id", nullable = false, updatable = false)
    private long applicationId;

    @Column(name = "parent_id")
    private @Nullable Long parentId;

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "resource_type", nullable = false, length = 16, updatable = false)
    private ResourceType type = ResourceType.MODULE;

    @Column(name = "code", nullable = false, length = CODE_MAX)
    private String code = "";

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name = "";

    @Column(name = "description", length = DESCRIPTION_MAX)
    private @Nullable String description;

    @Column(name = "route", length = CODE_MAX)
    private @Nullable String route;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "path", nullable = false, length = 400)
    private String path = "";

    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(name = "visible", nullable = false)
    private boolean visible = true;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "deny_mode", nullable = false, length = 16)
    private DenyMode denyMode = DenyMode.HIDE;

    @Column(name = "builtin", nullable = false)
    private boolean builtin;

    /** For JPA. */
    protected Resource()
    {
    }

    /**
     * Creates a resource below a parent, or at the top level of an application.
     *
     * @param applicationId the application
     * @param parent the parent, of the same application, or {@code null} for the top level
     * @param type what the resource stands for; never changes
     * @param code identifier unique in the application; see the class documentation
     * @param details the changeable settings
     * @param sortOrder position among its siblings
     * @return the resource, with its ID already assigned
     * @throws IllegalArgumentException if a value is invalid, the type may not sit below the parent, the parent
     *         belongs to another application or is at the maximum depth
     */
    public static Resource create(long applicationId, @Nullable Resource parent, ResourceType type, String code,
            ResourceDetails details, int sortOrder)
    {
        requireNonNull(type, "type");
        Resource resource = new Resource();
        long id = resource.preassignId();
        resource.applicationId = applicationId;
        resource.type = type;
        if (parent != null) {
            if (parent.applicationId != applicationId) {
                throw new IllegalArgumentException("the parent belongs to another application");
            }
            resource.parentId = parent.requireId();
            resource.depth = parent.depth + 1;
        }
        resource.requirePlacement(parent);
        if (resource.depth > MAX_DEPTH) {
            throw new IllegalArgumentException("resources nest at most " + (MAX_DEPTH + 1) + " levels");
        }
        resource.path = (parent == null ? "/" : parent.path) + id + "/";
        resource.recode(code);
        resource.update(details);
        resource.sortOrder = sortOrder;
        return resource;
    }

    /**
     * Checks that this resource's type may sit below a parent.
     *
     * @param parent the parent, or {@code null} for the top level
     * @throws IllegalArgumentException if it may not
     */
    public void requirePlacement(@Nullable Resource parent)
    {
        if (!canMoveBelow(parent)) {
            throw new IllegalArgumentException(type + " cannot be placed below " + (parent == null ? "the top level"
                    : parent.type));
        }
    }

    /**
     * Returns whether this resource's type may sit below a parent.
     *
     * @param parent the parent, or {@code null} for the top level
     * @return {@code true} if the placement is allowed
     */
    public boolean canMoveBelow(@Nullable Resource parent)
    {
        return type.allowsParent(parent == null ? null : parent.type);
    }

    /**
     * Changes the code.
     *
     * @param newCode the code; see the class documentation
     * @throws IllegalArgumentException if the code is invalid
     */
    public final void recode(String newCode)
    {
        String trimmed = Strings.requireNonBlank(newCode, "code");
        if (!CODE.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("code must be 1-255 letters, digits or . _ : / { } * @ -");
        }
        code = trimmed;
    }

    /**
     * Changes the settings.
     *
     * @param details the new settings
     * @throws IllegalArgumentException if a value is invalid, or a route is given for a type without one
     */
    public final void update(ResourceDetails details)
    {
        requireNonNull(details, "details");
        String newRoute = Strings.blankToNull(details.route());
        if (newRoute != null && (!ROUTED.contains(type) || !ROUTE.matcher(newRoute).matches())) {
            throw new IllegalArgumentException("only menus, pages and tabs have a route, which starts with '/'"
                    + " and has no spaces");
        }
        name = CatalogText.name(details.name(), NAME_MAX);
        description = CatalogText.optional(details.description(), DESCRIPTION_MAX, "description");
        route = newRoute;
        visible = details.visible();
        enabled = details.enabled();
        denyMode = details.denyMode();
    }

    /**
     * Marks the resource as declared by GrantForge itself, so it cannot be deleted or recoded by hand.
     *
     * @return this resource
     */
    public Resource markBuiltin()
    {
        builtin = true;
        return this;
    }

    /**
     * Returns whether a resource lies in this resource's subtree (itself included).
     *
     * @param other the other resource
     * @return {@code true} if {@code other} is this resource or below it
     */
    public boolean contains(Resource other)
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
     * Returns the application.
     *
     * @return the application ID
     */
    public long getApplicationId()
    {
        return applicationId;
    }

    /**
     * Returns the parent.
     *
     * @return the parent ID, or {@code null} at the top level
     */
    public @Nullable Long getParentId()
    {
        return parentId;
    }

    /**
     * Returns what the resource stands for.
     *
     * @return the type
     */
    public ResourceType getType()
    {
        return type;
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
     * Returns the changeable settings.
     *
     * @return the settings
     */
    public ResourceDetails getDetails()
    {
        return new ResourceDetails(name, description, route, visible, enabled, denyMode);
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
     * Returns the IDs from the top down to this resource, as {@code /top/.../this/}.
     *
     * @return the path
     */
    public String getPath()
    {
        return path;
    }

    /**
     * Returns the level; top-level resources are at 0.
     *
     * @return the depth
     */
    public int getDepth()
    {
        return depth;
    }

    /**
     * Returns whether the resource is declared by GrantForge itself.
     *
     * @return the flag
     */
    public boolean isBuiltin()
    {
        return builtin;
    }
}
