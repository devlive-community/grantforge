// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.identity.domain.AccountPosition;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.GroupMember;
import org.devlive.grantforge.identity.domain.GroupMemberRepository;
import org.devlive.grantforge.identity.domain.OrgMember;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.Position;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserGroup;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;

import java.time.Instant;

/**
 * The people and structures of the {@link CatalogFixture} tenant for assignment tests: {@code alice} is a member
 * of group {@code dev}, of department {@code sales} below {@code hq}, and holds position {@code cfo}.
 */
final class AssignmentFixture
{
    final long alice;
    final long dev;
    final long hq;
    final long sales;
    final long cfo;

    AssignmentFixture(CatalogFixture catalog, UserAccountRepository accounts, UserGroupRepository groups,
            GroupMemberRepository groupMembers, OrgUnitRepository units, OrgMemberRepository unitMembers,
            PositionRepository positions, AccountPositionRepository holdings)
    {
        alice = catalog.inTenant(() -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH).withDisplayName("Alice A"))
                .requireId());
        dev = catalog.inTenant(() -> groups.save(UserGroup.create("dev", "Developers", null)).requireId());
        OrgUnit top = catalog.inTenant(() -> units.save(OrgUnit.create(null, "hq", "HQ", 0)));
        hq = top.requireId();
        sales = catalog.inTenant(() -> units.save(OrgUnit.create(top, "sales", "Sales", 0)).requireId());
        cfo = catalog.inTenant(() -> positions.save(Position.create("cfo", "CFO", null, 0)).requireId());
        catalog.inTenant(() -> {
            groupMembers.save(GroupMember.of(dev, alice));
            unitMembers.save(OrgMember.of(alice, sales, true));
            return holdings.save(AccountPosition.of(alice, cfo));
        });
    }

    static void deleteRows(RoleAssignmentRepository assignments, RoleRepository roles, GroupMemberRepository groupMembers,
            OrgMemberRepository unitMembers, AccountPositionRepository holdings, UserGroupRepository groups, OrgUnitRepository units,
            PositionRepository positions)
    {
        TenantContext.callAsSystem(() -> {
            assignments.deleteAllInBatch();
            roles.deleteAllInBatch();
            groupMembers.deleteAllInBatch();
            unitMembers.deleteAllInBatch();
            holdings.deleteAllInBatch();
            groups.deleteAllInBatch();
            positions.deleteAllInBatch();
            units.findAll().stream().filter(unit -> unit.getParentId() != null).forEach(units::delete);
            units.deleteAllInBatch();
            return null;
        });
    }
}
