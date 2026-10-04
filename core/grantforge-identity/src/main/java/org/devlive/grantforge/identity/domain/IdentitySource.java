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
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A directory or provider a tenant's users sign in with instead of a password kept here (D-72). Its settings are JSON
 * of the type's settings record; the one secret (the directory's bind password or the client secret) is sealed.
 */
@Entity
@Table(name = "gf_identity_source")
public class IdentitySource
        extends TenantScopedEntity
{
    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code = "";

    @Column(name = "name", nullable = false, length = 128)
    private String name = "";

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source_type", nullable = false, updatable = false, length = 16)
    private IdentitySourceType type = IdentitySourceType.LDAP;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "provisioning", nullable = false)
    private boolean provisioning = true;

    @Column(name = "settings", nullable = false, length = 4000)
    private String settings = "{}";

    @Column(name = "secret", length = 1024)
    private @Nullable String secret;

    @Column(name = "sync_interval_minutes")
    private @Nullable Integer syncIntervalMinutes;

    @Column(name = "last_synced_at")
    private @Nullable Instant lastSyncedAt;

    @Column(name = "last_sync_summary", length = 512)
    private @Nullable String lastSyncSummary;

    /** For JPA. */
    protected IdentitySource()
    {
    }

    /**
     * Creates an enabled source.
     *
     * @param code the code, unique on the platform: it names the source in sign-in links
     * @param type the type
     * @return the source, without settings
     */
    public static IdentitySource create(String code, IdentitySourceType type)
    {
        IdentitySource source = new IdentitySource();
        source.code = requireNonNull(code, "code");
        source.type = requireNonNull(type, "type");
        return source;
    }

    /**
     * Changes what can change.
     *
     * @param newName what the console and the sign-in page show
     * @param on whether accounts may sign in with it
     * @param provision whether unknown users who sign in get an account
     * @param newSettings the settings, as JSON
     * @param interval minutes between automatic syncs, or {@code null} for none
     */
    public void configure(String newName, boolean on, boolean provision, String newSettings, @Nullable Integer interval)
    {
        this.name = requireNonNull(newName, "name");
        this.enabled = on;
        this.provisioning = provision;
        this.settings = requireNonNull(newSettings, "settings");
        this.syncIntervalMinutes = interval;
    }

    /**
     * Replaces the sealed secret.
     *
     * @param sealed the secret, sealed, or {@code null} for none
     */
    public void storeSecret(@Nullable String sealed)
    {
        this.secret = sealed;
    }

    /**
     * Records a sync.
     *
     * @param at when it ran
     * @param summary what it did, or why it failed
     */
    public void synced(Instant at, String summary)
    {
        this.lastSyncedAt = requireNonNull(at, "at");
        this.lastSyncSummary = summary.length() > 512 ? summary.substring(0, 512) : summary;
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
     * Returns the type.
     *
     * @return the type
     */
    public IdentitySourceType getType()
    {
        return type;
    }

    /**
     * Returns whether accounts may sign in with it.
     *
     * @return the flag
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Returns whether unknown users who sign in get an account.
     *
     * @return the flag
     */
    public boolean isProvisioning()
    {
        return provisioning;
    }

    /**
     * Returns the settings.
     *
     * @return JSON
     */
    public String getSettings()
    {
        return settings;
    }

    /**
     * Returns the sealed secret.
     *
     * @return the secret, or {@code null}
     */
    public @Nullable String getSecret()
    {
        return secret;
    }

    /**
     * Returns the minutes between automatic syncs.
     *
     * @return the interval, or {@code null} for none
     */
    public @Nullable Integer getSyncIntervalMinutes()
    {
        return syncIntervalMinutes;
    }

    /**
     * Returns when it was last synced.
     *
     * @return the time, or {@code null}
     */
    public @Nullable Instant getLastSyncedAt()
    {
        return lastSyncedAt;
    }

    /**
     * Returns what the last sync did.
     *
     * @return the summary, or {@code null}
     */
    public @Nullable String getLastSyncSummary()
    {
        return lastSyncSummary;
    }
}
