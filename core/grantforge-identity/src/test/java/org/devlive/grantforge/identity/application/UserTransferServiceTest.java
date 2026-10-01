// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.ConsoleSessionRepository;
import org.devlive.grantforge.identity.domain.OrgMember;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.Position;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserState;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, PasswordPolicy.class, PasswordService.class,
        ConsoleSessionService.class, UserAdminService.class, UserTransferService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserTransferServiceTest
{
    private static final String PASSWORD = "a long enough password";
    private static final List<String> IMPORT = List.of("username", "password", "displayName", "email", "primaryUnit",
            "otherUnits", "positions");

    @Autowired
    private UserTransferService service;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private OrgMemberRepository members;

    @Autowired
    private PositionRepository positions;

    @Autowired
    private AccountPositionRepository holdings;

    @Autowired
    private ConsoleSessionRepository sessions;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PasswordEncoder encoder;

    private long tenant;
    private long admin;
    private long hq;
    private long lab;

    @BeforeEach
    void createTenant()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        admin = inTenant(() -> accounts.save(UserAccount.create("admin", "h", Instant.EPOCH).markSystemAccount()).requireId());
        hq = inTenant(() -> units.save(OrgUnit.create(null, "hq", "总部", 0)).requireId());
        lab = inTenant(() -> units.save(OrgUnit.create(null, "lab", "实验室", 1)).requireId());
        inTenant(() -> positions.save(Position.create("cfo", "CFO", null, 0)));
        inTenant(() -> positions.save(Position.create("dev", "Developer", null, 1)));
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            sessions.deleteAllInBatch();
            members.deleteAllInBatch();
            holdings.deleteAllInBatch();
            positions.deleteAllInBatch();
            accounts.deleteAllInBatch();
            units.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
        events.deleteAllInBatch();
    }

    private <T> T inTenant(Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, action);
    }

    private static List<List<String>> file(List<String> header, List<String>... rows)
    {
        List<List<String>> records = new ArrayList<>();
        records.add(header);
        records.addAll(List.of(rows));
        return records;
    }

    @Test
    @SuppressWarnings("unchecked")
    void importsAccountsWithDepartmentsAndPositionsAllOrNothing()
    {
        List<List<String>> records = file(IMPORT,
                List.of("Alice", PASSWORD, "爱丽丝", "alice@acme.io", "HQ", "lab", "cfo; DEV"),
                List.of("bob", PASSWORD, "", "", "", "", ""));

        ImportReport checked = inTenant(() -> service.importUsers(admin, records, false));
        assertThat(checked).extracting(ImportReport::rows, ImportReport::created, ImportReport::applied).containsExactly(2, 0, false);
        assertThat(checked.problems()).isEmpty();
        assertThat(TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("alice"))).isEmpty();

        ImportReport applied = inTenant(() -> service.importUsers(admin, records, true));
        assertThat(applied).extracting(ImportReport::created, ImportReport::applied).containsExactly(2, true);
        UserAccount alice = TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("alice")).orElseThrow();
        assertThat(alice).extracting(UserAccount::getDisplayName, UserAccount::getEmail, UserAccount::isMustChangePassword)
                .containsExactly("爱丽丝", "alice@acme.io", true);
        assertThat(encoder.matches(PASSWORD, alice.getPasswordHash())).isTrue();
        assertThat(inTenant(() -> members.findByAccount(alice.requireId()))).extracting(OrgMember::getOrgUnitId,
                OrgMember::isPrimaryUnit).containsExactly(tuple(hq, true), tuple(lab, false));
        assertThat(inTenant(() -> holdings.findByAccountId(alice.requireId()))).hasSize(2);
        assertThat(events.findAll()).extracting(AuditEvent::getAction, AuditEvent::getReason)
                .containsExactly(tuple(AuditAction.USERS_IMPORTED, "2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsEveryProblemOfEveryRow()
    {
        List<List<String>> records = file(IMPORT,
                List.of("admin", PASSWORD, "", "", "", "", ""),
                List.of("carol", "short", "", "not mail", "", "", ""),
                List.of("CAROL", PASSWORD, "", "", "nowhere", "", "ceo"),
                List.of("a b", "", "x".repeat(129), "", "", "hq", ""),
                List.of("", PASSWORD, "", "", "", "", ""));

        ImportReport report = inTenant(() -> service.importUsers(admin, records, true));

        assertThat(report.applied()).isFalse();
        assertThat(report.problems()).extracting(ImportProblem::row, ImportProblem::column, ImportProblem::code).containsExactly(
                tuple(2, "username", IdentityErrorCode.USERNAME_TAKEN),
                tuple(3, "password", IdentityErrorCode.PASSWORD_TOO_SHORT),
                tuple(3, "email", IdentityErrorCode.IMPORT_INVALID_VALUE),
                tuple(4, "username", IdentityErrorCode.IMPORT_DUPLICATE),
                tuple(4, "primaryUnit", IdentityErrorCode.IMPORT_UNKNOWN_UNIT),
                tuple(4, "positions", IdentityErrorCode.IMPORT_UNKNOWN_POSITION),
                tuple(5, "username", IdentityErrorCode.IMPORT_INVALID_VALUE),
                tuple(5, "password", IdentityErrorCode.IMPORT_REQUIRED_VALUE),
                tuple(5, "displayName", IdentityErrorCode.IMPORT_INVALID_VALUE),
                tuple(5, "primaryUnit", IdentityErrorCode.IMPORT_REQUIRED_VALUE),
                tuple(6, "username", IdentityErrorCode.IMPORT_REQUIRED_VALUE));
        assertThat(report.problems().get(3).arguments()).containsExactly("CAROL");
        assertThat(TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("carol"))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportsWhatAnImportCanReadBack()
    {
        inTenant(() -> service.importUsers(admin, file(IMPORT,
                List.of("dora", PASSWORD, "多拉", "", "lab", "hq", "dev;cfo")), true));
        UserAccount dora = TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("dora")).orElseThrow();
        inTenant(() -> {
            UserAccount stored = accounts.findById(dora.requireId()).orElseThrow();
            stored.lockIndefinitely();
            return accounts.save(stored);
        });

        List<List<String>> exported = inTenant(() -> service.export(admin, new UserFilter("dora", null, null, false)));
        assertThat(exported).containsExactly(UserTransferService.EXPORT_COLUMNS,
                List.of("dora", "多拉", "", "LOCKED", "lab", "hq", "cfo;dev", ""));
        assertThat(inTenant(() -> service.export(admin, new UserFilter(null, UserState.ACTIVE, null, false))))
                .extracting(row -> row.get(0)).containsExactly("username", "admin");
    }
}
