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
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * That a subject (account, group, department or position) has a role, optionally only for a while. Assignments to
 * a department can include its sub-departments.
 */
@Entity
@EntityListeners(AuthorizationChangeListener.class)
@Table(name = "gf_role_assignment")
public class RoleAssignment
        extends TenantScopedEntity
{
    @Column(name = "role_id", nullable = false, updatable = false)
    private long roleId;

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "subject_type", nullable = false, length = 16, updatable = false)
    private SubjectType subjectType = SubjectType.USER;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private long subjectId;

    @Column(name = "include_sub_units", nullable = false)
    private boolean includeSubUnits;

    @Column(name = "valid_from")
    private @Nullable Instant validFrom;

    @Column(name = "valid_to")
    private @Nullable Instant validTo;

    /** For JPA. */
    protected RoleAssignment()
    {
    }

    /**
     * Gives a role to a subject.
     *
     * @param roleId the role
     * @param subjectType what the subject is
     * @param subjectId the subject
     * @param terms validity and reach
     * @return the assignment
     * @throws IllegalArgumentException if the validity ends before it starts
     */
    public static RoleAssignment create(long roleId, SubjectType subjectType, long subjectId, Terms terms)
    {
        RoleAssignment assignment = new RoleAssignment();
        assignment.roleId = roleId;
        assignment.subjectType = requireNonNull(subjectType, "subjectType");
        assignment.subjectId = subjectId;
        assignment.change(terms);
        return assignment;
    }

    /**
     * Changes the validity and reach.
     *
     * @param terms the new terms
     * @throws IllegalArgumentException if the validity ends before it starts
     */
    public final void change(Terms terms)
    {
        requireNonNull(terms, "terms");
        Instant from = terms.validFrom();
        Instant to = terms.validTo();
        if (from != null && to != null && !to.isAfter(from)) {
            throw new IllegalArgumentException("the validity must end after it starts");
        }
        validFrom = from;
        validTo = to;
        // Only departments have sub-departments.
        includeSubUnits = subjectType == SubjectType.ORG_UNIT && terms.includeSubUnits();
    }

    /**
     * Returns whether the assignment applies at a time.
     *
     * @param now the time
     * @return {@code true} from its start (inclusive) to its end (exclusive)
     */
    public boolean isValidAt(Instant now)
    {
        Instant from = validFrom;
        Instant to = validTo;
        return (from == null || !now.isBefore(from)) && (to == null || now.isBefore(to));
    }

    /**
     * Returns the role.
     *
     * @return the role ID
     */
    public long getRoleId()
    {
        return roleId;
    }

    /**
     * Returns what the subject is.
     *
     * @return the subject type
     */
    public SubjectType getSubjectType()
    {
        return subjectType;
    }

    /**
     * Returns the subject.
     *
     * @return the subject ID
     */
    public long getSubjectId()
    {
        return subjectId;
    }

    /**
     * Returns the validity and reach.
     *
     * @return the terms
     */
    public Terms getTerms()
    {
        return new Terms(validFrom, validTo, includeSubUnits);
    }

    /**
     * How long and how widely an assignment applies.
     *
     * @param validFrom when it starts, or {@code null} for at once
     * @param validTo when it ends (exclusive), or {@code null} for never
     * @param includeSubUnits for a department: whether members of its sub-departments have the role too
     */
    public record Terms(@Nullable Instant validFrom, @Nullable Instant validTo, boolean includeSubUnits)
    {
        /** Without limits: from now on, forever, the department alone. */
        public static final Terms UNLIMITED = new Terms(null, null, false);
    }
}
