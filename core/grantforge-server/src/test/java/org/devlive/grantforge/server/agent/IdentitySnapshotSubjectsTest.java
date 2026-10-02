// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.authz.application.RoleHolders;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleParent;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.GroupMemberRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserGroup;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentitySnapshotSubjectsTest
{
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private final RoleRepository roles = mock(RoleRepository.class);
    private final RoleParentRepository parents = mock(RoleParentRepository.class);
    private final RoleHolders holders = mock(RoleHolders.class);
    private final UserGroupRepository groups = mock(UserGroupRepository.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final IdentitySnapshotSubjects subjects = new IdentitySnapshotSubjects(roles, parents, holders, groups, members, accounts);

    private static <T> T withId(T entity, long id)
    {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static Role role(long id, String code, boolean enabled)
    {
        Role role = withId(Role.create(code, code, null), id);
        role.enable(enabled);
        return role;
    }

    private static UserAccount account(long id, String username, AccountStatus status)
    {
        UserAccount account = withId(UserAccount.create(username, "hash", NOW), id);
        if (status == AccountStatus.DISABLED) {
            account.disable();
        }
        return account;
    }

    @Test
    void rolesAreHeldThroughEnabledInheritingRolesByActiveAccounts()
    {
        // senior inherits from analyst; intern (disabled) inherits from analyst too.
        when(roles.findAll()).thenReturn(List.of(role(1, "analyst", true), role(2, "senior", true), role(3, "intern", false),
                role(4, "gone", false)));
        when(parents.findAll()).thenReturn(List.of(RoleParent.of(2, 1), RoleParent.of(3, 1), RoleParent.of(9, 1)));
        when(holders.of(Set.of(1L, 2L), NOW)).thenReturn(Set.of(10L, 11L, 12L));
        when(holders.of(Set.of(2L), NOW)).thenReturn(Set.of());
        when(accounts.findAllById(Set.of(10L, 11L, 12L))).thenReturn(List.of(account(11, "zoe", AccountStatus.ACTIVE),
                account(10, "amy", AccountStatus.ACTIVE), account(12, "old", AccountStatus.DISABLED)));

        Map<String, List<String>> held = subjects.roleHolders(List.of("analyst", "senior", "gone", "unknown"), NOW);
        assertThat(held).containsExactly(Map.entry("analyst", List.of("amy", "zoe")), Map.entry("gone", List.of()),
                Map.entry("senior", List.of()), Map.entry("unknown", List.of()));
    }

    @Test
    void groupsHaveTheirActiveMembers()
    {
        UserGroup ops = withId(UserGroup.create("ops", "Ops", null), 5);
        when(groups.findByCode(anyString())).thenReturn(Optional.empty());
        when(groups.findByCode("ops")).thenReturn(Optional.of(ops));
        when(members.findAccountIdsByGroupIds(List.of(5L))).thenReturn(List.of(20L));
        when(accounts.findAllById(Set.of(20L))).thenReturn(List.of(account(20, "carol", AccountStatus.ACTIVE)));
        assertThat(subjects.groupMembers(List.of("ops", "spies"))).containsExactly(Map.entry("ops", List.of("carol")),
                Map.entry("spies", List.of()));
    }
}
