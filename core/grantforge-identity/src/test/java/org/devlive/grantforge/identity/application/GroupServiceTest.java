// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.identity.domain.GroupMemberRepository;
import org.devlive.grantforge.identity.domain.GroupRow;
import org.devlive.grantforge.identity.domain.MemberRow;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@RecordApplicationEvents
@Import({AuditLog.class, IdentityConfiguration.class, GroupService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GroupServiceTest
{
    @Autowired
    private ApplicationEvents published;

    @Autowired
    private GroupService service;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private UserGroupRepository groups;

    @Autowired
    private GroupMemberRepository members;

    @Autowired
    private AuditEventRepository events;

    private long tenant;
    private long admin;
    private long alice;
    private long bob;

    @BeforeEach
    void createTenant()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        admin = inTenant(() -> accounts.save(UserAccount.create("admin", "h", Instant.EPOCH).markSystemAccount()).requireId());
        alice = inTenant(() -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH)).requireId());
        bob = inTenant(() -> accounts.save(UserAccount.create("bob", "h", Instant.EPOCH)).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            members.deleteAllInBatch();
            groups.deleteAllInBatch();
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
        events.deleteAllInBatch();
    }

    private <T> T inTenant(Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, action);
    }

    private static ErrorCode codeOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    @Test
    void administratorsCreateEditListAndDeleteGroups()
    {
        GroupRow ops = inTenant(() -> service.create(admin, "Ops", "运维组", "值班"));
        assertThat(ops).extracting(GroupRow::code, GroupRow::name, GroupRow::members).containsExactly("ops", "运维组", 0L);

        assertThatThrownBy(() -> inTenant(() -> service.create(admin, "OPS", "Again", null)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.GROUP_CODE_TAKEN));
        assertThatThrownBy(() -> inTenant(() -> service.create(admin, "bad code", "X", null)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));

        GroupRow dev = inTenant(() -> service.create(admin, "dev", "Developers", null));
        assertThatThrownBy(() -> inTenant(() -> service.update(admin, dev.id(), "ops", "Dev", null)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.GROUP_CODE_TAKEN));
        assertThat(inTenant(() -> service.update(admin, dev.id(), "dev", "开发组", "写代码")))
                .extracting(GroupRow::name, GroupRow::description).containsExactly("开发组", "写代码");
        assertThat(inTenant(() -> service.list(admin, "开发", new PageQuery(1, 10))).items())
                .extracting(GroupRow::code).containsExactly("dev");

        inTenant(() -> {
            service.addMembers(admin, dev.id(), List.of(alice));
            service.delete(admin, dev.id());
            return null;
        });
        assertThat(inTenant(() -> service.list(admin, null, new PageQuery(1, 10))).total()).isOne();
        assertThat(published.stream(IdentityDeleted.class)).containsExactly(new IdentityDeleted(IdentityDeleted.Kind.GROUP, dev.id()));
        assertThat(inTenant(() -> accounts.findById(alice))).isPresent();
        assertThatThrownBy(() -> inTenant(() -> service.update(admin, dev.id(), "dev", "Dev", null)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void addsAndRemovesMembersInBatches()
    {
        long ops = inTenant(() -> service.create(admin, "ops", "Ops", null)).id();

        assertThat(inTenant(() -> service.addMembers(admin, ops, List.of(alice, bob, alice)))).isEqualTo(2);
        // Adding members that already belong changes nothing.
        assertThat(inTenant(() -> service.addMembers(admin, ops, List.of(alice)))).isZero();
        assertThat(inTenant(() -> service.members(admin, ops, null, new PageQuery(1, 10))).items())
                .extracting(MemberRow::username).containsExactly("alice", "bob");
        assertThat(inTenant(() -> service.list(admin, null, new PageQuery(1, 10))).items().get(0).members()).isEqualTo(2);
        assertThat(inTenant(() -> service.update(admin, ops, "ops", "Ops", null)).members()).isEqualTo(2);

        assertThat(inTenant(() -> service.removeMembers(admin, ops, List.of(bob, -1L)))).isOne();
        assertThat(inTenant(() -> service.removeMembers(admin, ops, List.of(bob)))).isZero();
        assertThat(inTenant(() -> service.members(admin, ops, "ali", new PageQuery(1, 10))).total()).isOne();
        assertThat(events.findAll()).extracting(AuditEvent::getAction, AuditEvent::getReason).contains(
                tuple(AuditAction.GROUP_MEMBERS_ADDED, "2"),
                tuple(AuditAction.GROUP_MEMBERS_REMOVED, "1"));
    }

    @Test
    void refusesUnknownAccountsUnknownGroupsAndOversizedBatches()
    {
        long ops = inTenant(() -> service.create(admin, "ops", "Ops", null)).id();
        List<Long> tooMany = LongStream.rangeClosed(1, GroupService.MAX_BATCH + 1).boxed().toList();

        assertThatThrownBy(() -> inTenant(() -> service.addMembers(admin, ops, List.of(alice, -1L))))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(inTenant(() -> service.members(admin, ops, null, new PageQuery(1, 10))).total()).isZero();
        assertThatThrownBy(() -> inTenant(() -> service.addMembers(admin, -1, List.of(alice))))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> inTenant(() -> service.members(admin, -1, null, new PageQuery(1, 10))))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> inTenant(() -> service.removeMembers(admin, ops, tooMany)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> inTenant(() -> {
            service.delete(admin, -1);
            return null;
        })).satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
