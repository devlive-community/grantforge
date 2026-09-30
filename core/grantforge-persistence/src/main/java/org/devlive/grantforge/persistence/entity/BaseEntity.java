// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import org.devlive.grantforge.persistence.id.Tsids;
import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Base class of every entity: TSID primary key, optimistic-lock version and audit timestamps.
 *
 * <p>The ID and timestamps are assigned in {@link #beforeInsert()} rather than in a constructor because
 * JPA calls the constructor for every row it loads; generating IDs there would waste IDs and contend on
 * the generator lock. Until an entity is persisted, {@link #getId()} is {@code null} and
 * {@link #requireId()} fails.
 *
 * <p>{@code version} is a wrapper type on purpose: Spring Data treats an entity with a {@code null}
 * version as new and persists it instead of merging.
 *
 * <p>Timestamps are UTC and truncated to microseconds, the finest precision every supported database
 * stores, so values read back equal the values written.
 */
@MappedSuperclass
// Abstract without abstract methods on purpose: it is only meaningful as the superclass of an entity.
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class BaseEntity
{
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private @Nullable Long id;

    @Version
    @Column(name = "version", nullable = false)
    private @Nullable Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    /** For JPA and subclasses. */
    protected BaseEntity()
    {
    }

    /**
     * Returns the ID.
     *
     * @return the ID, or {@code null} before the entity is persisted
     */
    public @Nullable Long getId()
    {
        return id;
    }

    /**
     * Returns the ID of a persisted entity.
     *
     * @return the ID
     * @throws IllegalStateException if the entity has not been persisted yet
     */
    public long requireId()
    {
        Long current = id;
        if (current == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " has not been persisted yet");
        }
        return current;
    }

    /**
     * Returns the optimistic-lock version.
     *
     * @return the version, or {@code null} before the entity is persisted
     */
    public @Nullable Long getVersion()
    {
        return version;
    }

    /**
     * Returns when the entity was inserted.
     *
     * @return the creation time, or {@code null} before the entity is persisted
     */
    public @Nullable Instant getCreatedAt()
    {
        return createdAt;
    }

    /**
     * Returns when the entity was last written.
     *
     * @return the last modification time, or {@code null} before the entity is persisted
     */
    public @Nullable Instant getUpdatedAt()
    {
        return updatedAt;
    }

    @PrePersist
    void beforeInsert()
    {
        if (id == null) {
            id = Tsids.next();
        }
        Instant now = now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate()
    {
        updatedAt = now();
    }

    private static Instant now()
    {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Entities are equal when they are of the same (unproxied) class and have the same non-null ID.
     *
     * @param other the object to compare with
     * @return whether both denote the same persisted row
     */
    @Override
    public final boolean equals(@Nullable Object other)
    {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity entity) || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        return id != null && id.equals(entity.id);
    }

    /**
     * Constant per class so the hash code does not change when the ID is assigned on insert, which would
     * otherwise lose entities stored in hash-based collections before they were persisted.
     *
     * @return the hash code of the entity class
     */
    @Override
    public final int hashCode()
    {
        return Hibernate.getClass(this).hashCode();
    }

    @Override
    public String toString()
    {
        return Hibernate.getClass(this).getSimpleName() + "[id=" + id + "]";
    }
}
