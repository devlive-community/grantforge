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
import org.devlive.grantforge.identity.domain.AccountPosition;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.MemberRow;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.PositionRow;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@RecordApplicationEvents
@Import({AuditLog.class, IdentityConfiguration.class, PositionService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PositionServiceTest
{
    @Autowired
    private ApplicationEvents published;

    @Autowired
    private PositionService service;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PositionRepository positions;

    @Autowired
    private AccountPositionRepository holdings;

    @Autowired
    private AuditEventRepository events;

    private long tenant;
    private long admin;
    private long alice;

    @BeforeEach
    void createTenant()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        admin = inTenant(() -> accounts.save(UserAccount.create("admin", "h", Instant.EPOCH).markSystemAccount()).requireId());
        alice = inTenant(() -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH)).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            holdings.deleteAllInBatch();
            positions.deleteAllInBatch();
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
    void administratorsMaintainPositions()
    {
        PositionRow cfo = inTenant(() -> service.create(admin, "CFO", "财务总监", null, 2));
        PositionRow dev = inTenant(() -> service.create(admin, "dev", "Developer", "写代码", 1));
        assertThat(cfo).extracting(PositionRow::code, PositionRow::holders).containsExactly("cfo", 0L);

        assertThatThrownBy(() -> inTenant(() -> service.create(admin, "cfo", "Again", null, 0)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.POSITION_CODE_TAKEN));
        assertThatThrownBy(() -> inTenant(() -> service.create(admin, "x", "X", null, -1)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> inTenant(() -> service.update(admin, dev.id(), "cfo", "Dev", null, 0)))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(IdentityErrorCode.POSITION_CODE_TAKEN));

        inTenant(() -> holdings.save(AccountPosition.of(alice, cfo.id())));
        assertThat(inTenant(() -> service.update(admin, cfo.id(), "cfo", "CFO", "管钱", 0)))
                .extracting(PositionRow::name, PositionRow::description, PositionRow::holders).containsExactly("CFO", "管钱", 1L);
        assertThat(inTenant(() -> service.list(admin, " ", new PageQuery(1, 10))).items()).extracting(PositionRow::code)
                .containsExactly("cfo", "dev");
        assertThat(inTenant(() -> service.list(admin, "写", new PageQuery(1, 10))).total()).isZero();
        assertThat(inTenant(() -> service.options(admin))).extracting(UserPosition::name).containsExactly("CFO", "Developer");
        assertThat(inTenant(() -> service.holders(admin, cfo.id(), new PageQuery(1, 10))).items())
                .extracting(MemberRow::username).containsExactly("alice");

        inTenant(() -> {
            service.delete(admin, cfo.id());
            return null;
        });
        assertThat(inTenant(() -> holdings.findByAccountId(alice))).isEmpty();
        assertThat(published.stream(IdentityDeleted.class)).containsExactly(new IdentityDeleted(IdentityDeleted.Kind.POSITION, cfo.id()));
        assertThatThrownBy(() -> inTenant(() -> service.holders(admin, cfo.id(), new PageQuery(1, 10))))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(events.findAll()).extracting(AuditEvent::getAction).contains(AuditAction.POSITION_CREATED,
                AuditAction.POSITION_UPDATED, AuditAction.POSITION_DELETED);
    }
}
