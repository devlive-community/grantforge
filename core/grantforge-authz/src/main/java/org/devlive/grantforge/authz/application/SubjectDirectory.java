// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.identity.domain.AccountPosition;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.GroupMember;
import org.devlive.grantforge.identity.domain.GroupMemberRepository;
import org.devlive.grantforge.identity.domain.OrgMember;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * Reads the accounts, groups, departments and positions of the bound tenant for role assignments: their names,
 * and which of them an account belongs to. Must be called within a transaction with the tenant bound.
 */
@Component
public final class SubjectDirectory
{
    private final UserAccountRepository accounts;
    private final UserGroupRepository groups;
    private final GroupMemberRepository groupMembers;
    private final OrgUnitRepository units;
    private final OrgMemberRepository unitMembers;
    private final PositionRepository positions;
    private final AccountPositionRepository holdings;

    /**
     * Creates the directory.
     *
     * @param accounts accounts
     * @param groups user groups
     * @param groupMembers group memberships
     * @param units departments
     * @param unitMembers department memberships
     * @param positions positions
     * @param holdings who holds which position
     */
    public SubjectDirectory(UserAccountRepository accounts, UserGroupRepository groups, GroupMemberRepository groupMembers,
            OrgUnitRepository units, OrgMemberRepository unitMembers, PositionRepository positions,
            AccountPositionRepository holdings)
    {
        this.accounts = requireNonNull(accounts, "accounts");
        this.groups = requireNonNull(groups, "groups");
        this.groupMembers = requireNonNull(groupMembers, "groupMembers");
        this.units = requireNonNull(units, "units");
        this.unitMembers = requireNonNull(unitMembers, "unitMembers");
        this.positions = requireNonNull(positions, "positions");
        this.holdings = requireNonNull(holdings, "holdings");
    }

    /**
     * Finds one subject.
     *
     * @param type what it is
     * @param id its ID
     * @return the subject, if it exists in the bound tenant
     */
    public Optional<Subject> find(SubjectType type, long id)
    {
        return Optional.ofNullable(names(type, List.of(id)).get(id));
    }

    /**
     * Names subjects of one type.
     *
     * @param type what they are
     * @param ids their IDs
     * @return the subjects that exist, by ID
     */
    public Map<Long, Subject> names(SubjectType type, Collection<Long> ids)
    {
        Map<Long, Subject> found = new HashMap<>();
        if (ids.isEmpty()) {
            return found;
        }
        switch (type) {
            case USER -> accounts.findAllById(ids).forEach(account -> found.put(account.requireId(), new Subject(type,
                    account.requireId(), requireNonNullElse(account.getDisplayName(), account.getUsername()),
                    account.getUsername())));
            case GROUP -> groups.findAllById(ids).forEach(group -> found.put(group.requireId(), new Subject(type,
                    group.requireId(), group.getName(), group.getCode())));
            case ORG_UNIT -> units.findAllById(ids).forEach(unit -> found.put(unit.requireId(), new Subject(type,
                    unit.requireId(), unit.getName(), unit.getCode())));
            case POSITION -> positions.findAllById(ids).forEach(position -> found.put(position.requireId(), new Subject(type,
                    position.requireId(), position.getName(), position.getCode())));
        }
        return found;
    }

    /**
     * Returns the subjects an account has roles through: itself, its groups, its departments, their parent
     * departments, and its positions.
     *
     * @param accountId the account
     * @return the memberships
     */
    public Memberships memberships(long accountId)
    {
        Set<Long> groupIds = new HashSet<>(groupMembers.findByAccountId(accountId).stream().map(GroupMember::getGroupId).toList());
        Set<Long> unitIds = new HashSet<>(unitMembers.findByAccount(accountId).stream().map(OrgMember::getOrgUnitId).toList());
        Set<Long> parents = new HashSet<>();
        for (OrgUnit unit : units.findAllById(unitIds)) {
            for (String segment : unit.getPath().split("/")) {
                if (!segment.isEmpty() && !unitIds.contains(Long.parseLong(segment))) {
                    parents.add(Long.parseLong(segment));
                }
            }
        }
        Set<Long> positionIds = new HashSet<>(holdings.findByAccountId(accountId).stream().map(AccountPosition::getPositionId)
                .toList());
        return new Memberships(accountId, groupIds, unitIds, parents, positionIds);
    }

    /**
     * What an account belongs to.
     *
     * @param accountId the account
     * @param groups its user groups
     * @param units the departments it is a member of
     * @param parentUnits the departments above those, whose assignments reach it when they include sub-departments
     * @param positions the positions it holds
     */
    public record Memberships(long accountId, Set<Long> groups, Set<Long> units, Set<Long> parentUnits, Set<Long> positions)
    {
        /** Copies the sets. */
        public Memberships
        {
            groups = Set.copyOf(groups);
            units = Set.copyOf(units);
            parentUnits = Set.copyOf(parentUnits);
            positions = Set.copyOf(positions);
        }
    }
}
