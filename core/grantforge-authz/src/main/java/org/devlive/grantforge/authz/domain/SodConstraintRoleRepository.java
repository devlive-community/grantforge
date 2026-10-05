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

/** Roles of separation-of-duties constraints. */
public interface SodConstraintRoleRepository
        extends JpaRepository<SodConstraintRole, Long>
{
    /**
     * Lists the roles of some constraints.
     *
     * @param constraintIds the constraints
     * @return their role links
     */
    List<SodConstraintRole> findByConstraintIdIn(Collection<Long> constraintIds);

    /**
     * Removes the roles of a constraint.
     *
     * @param constraintId the constraint
     * @return how many went
     */
    @Modifying
    @Query("delete from SodConstraintRole r where r.constraintId = :constraint")
    int deleteByConstraint(@Param("constraint") long constraintId);
}
