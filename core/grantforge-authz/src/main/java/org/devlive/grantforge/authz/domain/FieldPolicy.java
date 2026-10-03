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
import org.devlive.grantforge.persistence.authz.AuthorizationChangeListener;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * How holders of a role see and change one secured field: visible, masked or hidden, and editable or read-only. Fields a
 * role says nothing about are left to the reader's other roles; fields no role of the reader mentions are visible and
 * editable.
 */
@Entity
@EntityListeners(AuthorizationChangeListener.class)
@Table(name = "gf_field_policy")
public class FieldPolicy
        extends TenantScopedEntity
{
    @Column(name = "role_id", nullable = false, updatable = false)
    private long roleId;

    @Column(name = "entity_code", nullable = false, updatable = false, length = 64)
    private String entityCode = "";

    @Column(name = "field_code", nullable = false, updatable = false, length = 64)
    private String fieldCode = "";

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "read_mode", nullable = false, length = 8)
    private FieldReadMode readMode = FieldReadMode.VISIBLE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mask_strategy", length = 16)
    private @Nullable MaskStrategy maskStrategy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "write_mode", nullable = false, length = 8)
    private FieldWriteMode writeMode = FieldWriteMode.EDITABLE;

    /** For JPA. */
    protected FieldPolicy()
    {
    }

    /**
     * Creates a policy.
     *
     * @param roleId the role
     * @param entityCode the field's entity
     * @param fieldCode the field
     * @param readMode how holders see it
     * @param maskStrategy how it is masked; only for masked fields
     * @param writeMode whether holders may change it
     * @return the policy
     * @throws IllegalArgumentException if a mask strategy is given for a field that is not masked, or missing for one that is
     */
    public static FieldPolicy create(long roleId, String entityCode, String fieldCode, FieldReadMode readMode,
            @Nullable MaskStrategy maskStrategy, FieldWriteMode writeMode)
    {
        if ((requireNonNull(readMode, "readMode") == FieldReadMode.MASKED) != (maskStrategy != null)) {
            throw new IllegalArgumentException("a mask strategy goes with masked fields, and only with them");
        }
        FieldPolicy policy = new FieldPolicy();
        policy.roleId = roleId;
        policy.entityCode = requireNonNull(entityCode, "entityCode");
        policy.fieldCode = requireNonNull(fieldCode, "fieldCode");
        policy.readMode = readMode;
        policy.maskStrategy = maskStrategy;
        policy.writeMode = requireNonNull(writeMode, "writeMode");
        return policy;
    }

    /**
     * Copies the policy to another role, as copying a role does.
     *
     * @param otherRoleId the other role
     * @return the copy
     */
    public FieldPolicy copyTo(long otherRoleId)
    {
        return create(otherRoleId, entityCode, fieldCode, readMode, maskStrategy, writeMode);
    }

    /**
     * Returns the role.
     *
     * @return its ID
     */
    public long getRoleId()
    {
        return roleId;
    }

    /**
     * Returns the field's entity.
     *
     * @return the entity's code
     */
    public String getEntityCode()
    {
        return entityCode;
    }

    /**
     * Returns the field.
     *
     * @return the field's code
     */
    public String getFieldCode()
    {
        return fieldCode;
    }

    /**
     * Returns how holders see the field.
     *
     * @return the read mode
     */
    public FieldReadMode getReadMode()
    {
        return readMode;
    }

    /**
     * Returns how the field is masked.
     *
     * @return the strategy, or {@code null} unless masked
     */
    public @Nullable MaskStrategy getMaskStrategy()
    {
        return maskStrategy;
    }

    /**
     * Returns whether holders may change the field.
     *
     * @return the write mode
     */
    public FieldWriteMode getWriteMode()
    {
        return writeMode;
    }
}
