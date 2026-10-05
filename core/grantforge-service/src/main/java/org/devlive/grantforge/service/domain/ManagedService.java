// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A service of a tenant whose permissions a plugin manages, such as an HDFS cluster. Its configuration is stored as
 * JSON: plain values in {@code config}, secrets encrypted in {@code secrets}.
 */
@Entity
@Table(name = "gf_service")
public class ManagedService
        extends TenantScopedEntity
{
    @Column(name = "name", nullable = false, length = 64)
    private String name = "";

    @Column(name = "label", nullable = false, length = 128)
    private String label = "";

    @Column(name = "description", length = 512)
    private @Nullable String description;

    @Column(name = "service_type", nullable = false, updatable = false, length = 64)
    private String serviceType = "";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    // Long text: TEXT, LONGTEXT, CLOB or NVARCHAR(MAX) as the database has it (${longtext} in the changelog); the
    // length makes the MySQL and MariaDB dialects expect LONGTEXT rather than TINYTEXT.
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "config", nullable = false, length = Length.LONG32)
    private String config = "{}";

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "secrets", nullable = false, length = Length.LONG32)
    private String secrets = "{}";

    @Column(name = "policy_version", nullable = false)
    private long policyVersion;

    /** For JPA. */
    protected ManagedService()
    {
    }

    /**
     * Creates a service.
     *
     * @param serviceType the service type's name
     * @param name the service's name
     * @param label what the console shows
     * @param description a longer explanation, or {@code null}
     * @return the service, enabled and without configuration
     */
    public static ManagedService create(String serviceType, String name, String label, @Nullable String description)
    {
        ManagedService service = new ManagedService();
        service.serviceType = requireNonNull(serviceType, "serviceType");
        service.describe(name, label, description);
        return service;
    }

    /**
     * Changes the name and description.
     *
     * @param newName the service's name
     * @param newLabel what the console shows
     * @param newDescription a longer explanation, or {@code null}
     */
    public final void describe(String newName, String newLabel, @Nullable String newDescription)
    {
        this.name = requireNonNull(newName, "name");
        this.label = requireNonNull(newLabel, "label");
        this.description = newDescription;
    }

    /**
     * Stores the configuration.
     *
     * @param values the plain values, as JSON
     * @param encrypted the encrypted secrets, as JSON
     */
    public void configure(String values, String encrypted)
    {
        this.config = requireNonNull(values, "values");
        this.secrets = requireNonNull(encrypted, "encrypted");
    }

    /**
     * Enables or disables the service.
     *
     * @param on whether it is in use
     */
    public void enable(boolean on)
    {
        this.enabled = on;
    }

    /** Notes that a policy of the service was added, changed or removed. */
    public void policiesChanged()
    {
        policyVersion++;
    }

    /**
     * Returns how often the service's policies changed, so copies of them can tell whether they are current.
     *
     * @return the number of changes
     */
    public long getPolicyVersion()
    {
        return policyVersion;
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
     * Returns what the console shows.
     *
     * @return the label
     */
    public String getLabel()
    {
        return label;
    }

    /**
     * Returns the longer explanation.
     *
     * @return the description, or {@code null}
     */
    public @Nullable String getDescription()
    {
        return description;
    }

    /**
     * Returns the service type.
     *
     * @return its name
     */
    public String getServiceType()
    {
        return serviceType;
    }

    /**
     * Returns whether the service is in use.
     *
     * @return {@code true} if enabled
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Returns the plain configuration values.
     *
     * @return JSON
     */
    public String getConfig()
    {
        return config;
    }

    /**
     * Returns the encrypted secrets.
     *
     * @return JSON
     */
    public String getSecrets()
    {
        return secrets;
    }
}
