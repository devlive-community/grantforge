// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An isolated organisation. Tenants are platform-level records: every tenant-scoped row points to one.
 *
 * <p>{@code authzVersion} is incremented whenever authorisation data of the tenant changes; caches and the
 * console compare it to decide whether a permission snapshot is stale.
 */
@Entity
@Table(name = "gf_tenant")
public class Tenant
        extends BaseEntity
{
    /** Longest tenant name. */
    public static final int NAME_MAX = 128;
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9-]{1,63}");

    @Column(name = "code", nullable = false, length = 64)
    private String code = "";

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name = "";

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "authz_version", nullable = false)
    private long authzVersion;

    /** For JPA. */
    protected Tenant()
    {
    }

    /**
     * Creates an active tenant whose ID is known immediately, so work can be bound to it before it is saved.
     *
     * @param code stable identifier: lowercase letters, digits and hyphens, 2-64 characters, starting with a letter
     * @param name display name, 1-{@value #NAME_MAX} characters after trimming
     * @return the new tenant
     * @throws IllegalArgumentException if the code or name is invalid
     */
    public static Tenant create(String code, String name)
    {
        String normalizedCode = Strings.requireNonBlank(code, "code").toLowerCase(Locale.ROOT);
        if (!CODE.matcher(normalizedCode).matches()) {
            throw new IllegalArgumentException("code must be 2-64 lowercase letters, digits or hyphens starting with a letter");
        }
        String trimmedName = Strings.requireNonBlank(name, "name");
        if (trimmedName.length() > NAME_MAX) {
            throw new IllegalArgumentException("name must be at most " + NAME_MAX + " characters");
        }
        Tenant tenant = new Tenant();
        tenant.code = normalizedCode;
        tenant.name = trimmedName;
        tenant.preassignId();
        return tenant;
    }

    /** Blocks sign-in for every account of the tenant; data is kept. */
    public void suspend()
    {
        status = TenantStatus.SUSPENDED;
    }

    /** Allows sign-in again. */
    public void activate()
    {
        status = TenantStatus.ACTIVE;
    }

    /**
     * Returns the stable code.
     *
     * @return the lowercase code
     */
    public String getCode()
    {
        return code;
    }

    /**
     * Returns the display name.
     *
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns the lifecycle state.
     *
     * @return the status
     */
    public TenantStatus getStatus()
    {
        return status;
    }

    /**
     * Returns the authorisation data version.
     *
     * @return a counter that only increases
     */
    public long getAuthzVersion()
    {
        return authzVersion;
    }
}
