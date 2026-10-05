// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Separation-of-duties constraints of the bound tenant. */
public interface SodConstraintRepository
        extends JpaRepository<SodConstraint, Long>
{
    /**
     * Finds a constraint by code.
     *
     * @param code the code
     * @return the constraint
     */
    Optional<SodConstraint> findByCode(String code);

    /**
     * Lists the constraints by name.
     *
     * @return the constraints
     */
    List<SodConstraint> findAllByOrderByNameAsc();

    /**
     * Lists the constraints that apply.
     *
     * @return the enabled constraints
     */
    List<SodConstraint> findByEnabledTrue();
}
