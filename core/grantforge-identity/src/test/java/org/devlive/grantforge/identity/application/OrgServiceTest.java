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
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, OrgService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrgServiceTest
{
    @Autowired
    private OrgService service;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long tenant;
    private long admin;
    private long member;

    @BeforeEach
    void createTenant()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        admin = inTenant(() -> accounts.save(UserAccount.create("admin", "h", Instant.EPOCH).markSystemAccount()).requireId());
        member = inTenant(() -> accounts.save(UserAccount.create("member", "h", Instant.EPOCH)).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.runInTenant(tenant, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    List<OrgUnit> all = units.findTree();
                    for (int i = all.size() - 1; i >= 0; i--) {
                        units.delete(all.get(i));
                    }
                }));
        TenantContext.callAsSystem(() -> {
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

    private long create(@Nullable Long parent, String code)
    {
        return inTenant(() -> service.create(admin, parent, code, code.toUpperCase())).id();
    }

    private List<String> tree()
    {
        // code@depth:parent-code, in tree order
        List<OrgUnitView> all = inTenant(() -> service.tree());
        return all.stream().map(unit -> unit.code() + "@" + unit.depth() + ":" + all.stream()
                .filter(parent -> parent.id() == (unit.parentId() == null ? -1 : unit.parentId()))
                .map(OrgUnitView::code).findFirst().orElse("-")).toList();
    }

    @Test
    void administratorsBuildTheTreeAndEveryoneReadsIt()
    {
        long hq = create(null, "hq");
        create(hq, "sales");
        create(hq, "rnd");
        create(null, "lab");

        assertThat(tree()).containsExactly("hq@0:-", "lab@0:-", "sales@1:hq", "rnd@1:hq");
        assertThatThrownBy(() -> inTenant(() -> service.create(member, null, "x", "X")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> inTenant(() -> service.create(admin, -1L, "x", "X")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> inTenant(() -> service.create(admin, null, "HQ", "Again")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.ORG_CODE_TAKEN));
        assertThatThrownBy(() -> inTenant(() -> service.create(admin, null, "bad code", "X")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
    }

    @Test
    void renamesDepartmentsKeepingCodesUnique()
    {
        long hq = create(null, "hq");
        create(null, "lab");

        assertThat(inTenant(() -> service.update(admin, hq, "HQ", "总部"))).extracting(OrgUnitView::code, OrgUnitView::name)
                .containsExactly("hq", "总部");
        assertThatThrownBy(() -> inTenant(() -> service.update(admin, hq, "lab", "HQ")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.ORG_CODE_TAKEN));
        assertThatThrownBy(() -> inTenant(() -> service.update(admin, hq, "hq", " ")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
    }

    @Test
    void movesSubtreesAndReordersSiblings()
    {
        long hq = create(null, "hq");
        long sales = create(hq, "sales");
        create(sales, "east");
        long lab = create(null, "lab");
        create(lab, "ai");

        inTenant(() -> service.move(admin, sales, lab, 0));
        assertThat(tree()).containsExactly("hq@0:-", "lab@0:-", "sales@1:lab", "ai@1:lab", "east@2:sales");

        inTenant(() -> service.move(admin, sales, lab, 99));
        assertThat(tree()).containsExactly("hq@0:-", "lab@0:-", "ai@1:lab", "sales@1:lab", "east@2:sales");

        inTenant(() -> service.move(admin, lab, null, 0));
        assertThat(tree()).startsWith("lab@0:-", "hq@0:-");

        inTenant(() -> service.move(admin, sales, null, -5));
        assertThat(tree()).containsExactly("sales@0:-", "lab@0:-", "hq@0:-", "ai@1:lab", "east@1:sales");
        assertThat(inTenant(() -> units.findById(sales)).orElseThrow().getPath()).isEqualTo("/" + sales + "/");
    }

    @Test
    void refusesCyclesAndTooDeepTrees()
    {
        long hq = create(null, "hq");
        long sales = create(hq, "sales");
        long east = create(sales, "east");

        assertThatThrownBy(() -> inTenant(() -> service.move(admin, hq, east, 0)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.ORG_MOVE_CYCLE));
        assertThatThrownBy(() -> inTenant(() -> service.move(admin, hq, hq, 0)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.ORG_MOVE_CYCLE));

        long deepest = east;
        for (int level = 3; level <= OrgUnit.MAX_DEPTH; level++) {
            deepest = create(deepest, "l" + level);
        }
        long last = deepest;
        assertThatThrownBy(() -> create(last, "too-deep"))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.ORG_TOO_DEEP));
        long other = create(null, "other");
        long otherChild = create(other, "other-child");
        // Moving a two-level subtree below the deepest level would exceed the limit.
        assertThatThrownBy(() -> inTenant(() -> service.move(admin, other, last, 0)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.ORG_TOO_DEEP));
        assertThat(inTenant(() -> units.findById(otherChild)).orElseThrow().getDepth()).isOne();
    }

    @Test
    void deletesOnlyLeavesAndAuditsEveryChange()
    {
        long hq = create(null, "hq");
        long sales = create(hq, "sales");

        assertThatThrownBy(() -> inTenant(() -> {
            service.delete(admin, hq);
            return null;
        })).satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.ORG_NOT_EMPTY));
        inTenant(() -> {
            service.move(admin, sales, null, 1);
            service.update(admin, sales, "sales", "Sales");
            service.delete(admin, sales);
            return null;
        });

        assertThat(tree()).containsExactly("hq@0:-");
        assertThat(events.findAll()).extracting(AuditEvent::getAction).containsExactlyInAnyOrder(
                AuditAction.ORG_UNIT_CREATED, AuditAction.ORG_UNIT_CREATED, AuditAction.ORG_UNIT_MOVED,
                AuditAction.ORG_UNIT_UPDATED, AuditAction.ORG_UNIT_DELETED);
    }
}
