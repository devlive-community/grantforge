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

import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * That an API returns or accepts a secured field, as the code declares it: the catalog shows where a field permission
 * takes effect. Kept in step with the code at every start.
 */
@Entity
@Table(name = "gf_field_usage")
public class FieldUsage
        extends BaseEntity
{
    @Column(name = "resource_id", nullable = false, updatable = false)
    private long resourceId;

    @Column(name = "http_method", nullable = false, length = 8, updatable = false)
    private String httpMethod = "";

    @Column(name = "path_pattern", nullable = false, length = 255, updatable = false)
    private String pathPattern = "";

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "direction", nullable = false, length = 8, updatable = false)
    private FieldDirection direction = FieldDirection.READ;

    /** For JPA. */
    protected FieldUsage()
    {
    }

    /**
     * Creates a usage.
     *
     * @param field the field's resource
     * @param httpMethod the API's HTTP method, such as {@code GET}
     * @param pathPattern the API's path pattern, such as {@code /api/v1/users/{id}}
     * @param direction whether the API returns or accepts the field
     * @return the usage
     * @throws IllegalArgumentException if the resource is no field
     */
    public static FieldUsage of(Resource field, String httpMethod, String pathPattern, FieldDirection direction)
    {
        if (field.getType() != ResourceType.FIELD) {
            throw new IllegalArgumentException(field.getCode() + " is no field");
        }
        FieldUsage usage = new FieldUsage();
        usage.resourceId = field.requireId();
        usage.httpMethod = requireNonNull(httpMethod, "httpMethod").toUpperCase(Locale.ROOT);
        usage.pathPattern = requireNonNull(pathPattern, "pathPattern");
        usage.direction = requireNonNull(direction, "direction");
        return usage;
    }

    /**
     * Returns the field.
     *
     * @return its resource's ID
     */
    public long getResourceId()
    {
        return resourceId;
    }

    /**
     * Returns the API's HTTP method.
     *
     * @return the method, in capitals
     */
    public String getHttpMethod()
    {
        return httpMethod;
    }

    /**
     * Returns the API's path pattern.
     *
     * @return the pattern
     */
    public String getPathPattern()
    {
        return pathPattern;
    }

    /**
     * Returns whether the API returns or accepts the field.
     *
     * @return the direction
     */
    public FieldDirection getDirection()
    {
        return direction;
    }
}
