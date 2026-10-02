// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.authz.application.RoleHolders;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleHierarchy;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.GroupMemberRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserGroup;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.devlive.grantforge.service.agent.SnapshotSubjects;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * The members of the bound tenant's roles and groups, for policy snapshots: a role is held by whoever holds it or a role
 * inheriting from it, directly or through groups, departments and positions; only enabled roles and active accounts
 * count. Must be called in a transaction with the tenant bound.
 */
@Component
public final class IdentitySnapshotSubjects
        implements SnapshotSubjects
{
    private final RoleRepository roles;
    private final RoleParentRepository parents;
    private final RoleHolders holders;
    private final UserGroupRepository groups;
    private final GroupMemberRepository members;
    private final UserAccountRepository accounts;

    /**
     * Creates the resolver.
     *
     * @param roles the roles of the bound tenant
     * @param parents which roles inherit from which
     * @param holders who holds roles
     * @param groups the groups of the bound tenant
     * @param members group memberships
     * @param accounts the accounts, for their names and status
     */
    public IdentitySnapshotSubjects(RoleRepository roles, RoleParentRepository parents, RoleHolders holders, UserGroupRepository groups,
            GroupMemberRepository members, UserAccountRepository accounts)
    {
        this.roles = requireNonNull(roles, "roles");
        this.parents = requireNonNull(parents, "parents");
        this.holders = requireNonNull(holders, "holders");
        this.groups = requireNonNull(groups, "groups");
        this.members = requireNonNull(members, "members");
        this.accounts = requireNonNull(accounts, "accounts");
    }

    @Override
    public Map<String, List<String>> roleHolders(Collection<String> roleCodes, Instant now)
    {
        List<Role> all = roles.findAll();
        Map<Long, Role> byId = all.stream().collect(Collectors.toMap(Role::requireId, Function.identity()));
        Map<String, Role> byCode = all.stream().collect(Collectors.toMap(Role::getCode, Function.identity()));
        RoleHierarchy hierarchy = new RoleHierarchy(parents.findAll());
        Map<String, List<String>> result = new TreeMap<>();
        for (String code : roleCodes) {
            Role role = byCode.get(code);
            result.put(code, role == null || !role.isEnabled() ? List.of() : names(holders.of(granting(role, hierarchy, byId), now)));
        }
        return result;
    }

    /** The role and the enabled roles inheriting from it: holders of any of them hold the role. */
    private static Set<Long> granting(Role role, RoleHierarchy hierarchy, Map<Long, Role> byId)
    {
        Set<Long> granting = new HashSet<>();
        granting.add(role.requireId());
        for (Long id : hierarchy.descendants(role.requireId()).keySet()) {
            Role inheriting = byId.get(id);
            if (inheriting != null && inheriting.isEnabled()) {
                granting.add(id);
            }
        }
        return granting;
    }

    @Override
    public Map<String, List<String>> groupMembers(Collection<String> groupCodes)
    {
        Map<String, List<String>> result = new TreeMap<>();
        for (String code : groupCodes) {
            result.put(code, groups.findByCode(code).map(UserGroup::requireId).map(this::membersOf).orElse(List.of()));
        }
        return result;
    }

    private List<String> membersOf(long groupId)
    {
        return names(Set.copyOf(members.findAccountIdsByGroupIds(List.of(groupId))));
    }

    private List<String> names(Set<Long> accountIds)
    {
        return accountIds.isEmpty() ? List.of() : accounts.findAllById(accountIds).stream()
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE).map(UserAccount::getUsername).sorted().toList();
    }
}
