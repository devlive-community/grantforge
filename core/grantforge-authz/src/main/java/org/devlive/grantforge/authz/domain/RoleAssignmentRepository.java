// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence of {@link RoleAssignment}s; queries are filtered to the bound tenant. */
public interface RoleAssignmentRepository
        extends JpaRepository<RoleAssignment, Long>
{
    /**
     * Returns who has a role, by creation.
     *
     * @param roleId the role
     * @return its assignments
     */
    @Query("select a from RoleAssignment a where a.roleId = :roleId order by a.createdAt, a.id")
    List<RoleAssignment> findByRole(@Param("roleId") long roleId);

    /**
     * Returns the assignments to some subjects of one type.
     *
     * @param subjectType what the subjects are
     * @param subjectIds the subjects; at most a few hundred
     * @return their assignments
     */
    @Query("select a from RoleAssignment a where a.subjectType = :subjectType and a.subjectId in :subjectIds")
    List<RoleAssignment> findBySubjects(@Param("subjectType") SubjectType subjectType,
            @Param("subjectIds") Collection<Long> subjectIds);

    /**
     * Finds the assignment of a role to a subject.
     *
     * @param roleId the role
     * @param subjectType what the subject is
     * @param subjectId the subject
     * @return the assignment, if any
     */
    Optional<RoleAssignment> findByRoleIdAndSubjectTypeAndSubjectId(long roleId, SubjectType subjectType, long subjectId);

    /**
     * Removes every assignment of a role.
     *
     * @param roleId the role
     * @return how many were removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RoleAssignment a where a.roleId = :roleId")
    int removeRole(@Param("roleId") long roleId);

    /**
     * Removes every assignment to a subject.
     *
     * @param subjectType what the subject is
     * @param subjectId the subject
     * @return how many were removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RoleAssignment a where a.subjectType = :subjectType and a.subjectId = :subjectId")
    int removeSubject(@Param("subjectType") SubjectType subjectType, @Param("subjectId") long subjectId);
}
