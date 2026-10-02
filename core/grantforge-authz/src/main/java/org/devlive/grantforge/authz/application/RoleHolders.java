// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.GroupMemberRepository;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.persistence.query.InClauseBatcher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Finds the accounts of the bound tenant holding roles: directly, through a group, a department (and, if the
 * assignment says so, its sub-departments) or a position, by the assignments valid now. Within a transaction.
 */
@Component
public final class RoleHolders
{
    private final RoleAssignmentRepository assignments;
    private final GroupMemberRepository groupMembers;
    private final OrgMemberRepository unitMembers;
    private final OrgUnitRepository units;
    private final AccountPositionRepository holdings;

    /**
     * Creates the finder.
     *
     * @param assignments role assignments
     * @param groupMembers group memberships
     * @param unitMembers department memberships
     * @param units departments, for sub-departments
     * @param holdings positions held
     */
    public RoleHolders(RoleAssignmentRepository assignments, GroupMemberRepository groupMembers, OrgMemberRepository unitMembers,
            OrgUnitRepository units, AccountPositionRepository holdings)
    {
        this.assignments = requireNonNull(assignments, "assignments");
        this.groupMembers = requireNonNull(groupMembers, "groupMembers");
        this.unitMembers = requireNonNull(unitMembers, "unitMembers");
        this.units = requireNonNull(units, "units");
        this.holdings = requireNonNull(holdings, "holdings");
    }

    /**
     * Returns the accounts holding any of the roles now.
     *
     * @param roleIds the roles
     * @param now the current time, for the validity of assignments
     * @return the accounts
     */
    public Set<Long> of(Collection<Long> roleIds, Instant now)
    {
        Map<SubjectType, List<RoleAssignment>> bySubject = roleIds.stream().distinct().flatMap(id -> assignments.findByRole(id).stream())
                .filter(assignment -> assignment.isValidAt(now)).collect(Collectors.groupingBy(RoleAssignment::getSubjectType));
        Set<Long> accounts = new HashSet<>(ids(bySubject, SubjectType.USER));
        accounts.addAll(InClauseBatcher.query(ids(bySubject, SubjectType.GROUP), groupMembers::findAccountIdsByGroupIds));
        accounts.addAll(InClauseBatcher.query(ids(bySubject, SubjectType.POSITION), holdings::findAccountIdsByPositionIds));
        List<RoleAssignment> toUnits = bySubject.getOrDefault(SubjectType.ORG_UNIT, List.of());
        if (!toUnits.isEmpty()) {
            List<OrgUnit> tree = units.findTree();
            Set<Long> unitIds = new HashSet<>();
            for (RoleAssignment assignment : toUnits) {
                unitIds.add(assignment.getSubjectId());
                if (assignment.getTerms().includeSubUnits()) {
                    tree.stream().filter(unit -> unit.getPath().contains("/" + assignment.getSubjectId() + "/"))
                            .forEach(unit -> unitIds.add(unit.requireId()));
                }
            }
            accounts.addAll(InClauseBatcher.query(unitIds, unitMembers::findAccountIdsByUnitIds));
        }
        return accounts;
    }

    private static Set<Long> ids(Map<SubjectType, List<RoleAssignment>> bySubject, SubjectType type)
    {
        return bySubject.getOrDefault(type, List.of()).stream().map(RoleAssignment::getSubjectId).collect(Collectors.toSet());
    }
}
