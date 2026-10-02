// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, OrgTransferService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrgTransferServiceTest
{
    @Autowired
    private OrgTransferService service;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long tenant;
    private long admin;

    @BeforeEach
    void createTenant()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        admin = inTenant(() -> accounts.save(UserAccount.create("admin", "h", Instant.EPOCH).markSystemAccount()).requireId());
        OrgUnit hq = OrgUnit.create(null, "hq", "总部", 0);
        inTenant(() -> units.saveAll(List.of(hq, OrgUnit.create(hq, "sales", "销售, 华东", 1))));
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.runInTenant(tenant, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<OrgUnit> all = new ArrayList<>(units.findTree());
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

    private static List<List<String>> file(List<String>... rows)
    {
        List<List<String>> records = new ArrayList<>();
        records.add(OrgTransferService.COLUMNS);
        records.addAll(List.of(rows));
        return records;
    }

    @Test
    void exportsTheTreeParentsFirst()
    {
        assertThat(inTenant(() -> service.export(admin))).containsExactly(OrgTransferService.COLUMNS,
                List.of("hq", "总部", "", "0"), List.of("sales", "销售, 华东", "hq", "1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void importsRowsInAnyOrderBelowExistingOrNewParents()
    {
        List<List<String>> records = file(List.of("east", "华东", "Region", "2"), List.of("REGION", "大区", "hq", ""),
                List.of("lab", "实验室", "", ""));

        ImportReport checked = inTenant(() -> service.importUnits(admin, records, false));
        assertThat(checked).extracting(ImportReport::rows, ImportReport::created, ImportReport::applied)
                .containsExactly(3, 0, false);
        assertThat(checked.problems()).isEmpty();
        assertThat(inTenant(() -> units.findTree())).hasSize(2);

        ImportReport applied = inTenant(() -> service.importUnits(admin, records, true));
        assertThat(applied).extracting(ImportReport::created, ImportReport::applied).containsExactly(3, true);
        assertThat(inTenant(() -> units.findByCode("east")).orElseThrow()).extracting(OrgUnit::getDepth, OrgUnit::getSortOrder)
                .containsExactly(2, 2);
        assertThat(events.findAll()).extracting(AuditEvent::getAction, AuditEvent::getReason)
                .containsExactly(tuple(AuditAction.ORG_UNITS_IMPORTED, "3"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsEveryProblemAndWritesNothing()
    {
        List<List<String>> records = file(
                List.of("hq", "Again", "", ""),
                List.of("a", "A", "b", ""),
                List.of("b", "B", "a", ""),
                List.of("c", "", "", ""),
                List.of("c", "C", "", "-1"),
                List.of("bad code", "X", "", ""),
                List.of("d", "D", "nowhere", ""),
                List.of("", "E", "", ""));

        ImportReport report = inTenant(() -> service.importUnits(admin, records, true));

        assertThat(report.applied()).isFalse();
        assertThat(report.problems()).extracting(ImportProblem::row, ImportProblem::code).containsExactly(
                tuple(2, IdentityErrorCode.ORG_CODE_TAKEN),
                tuple(5, IdentityErrorCode.IMPORT_REQUIRED_VALUE),
                tuple(6, IdentityErrorCode.IMPORT_INVALID_VALUE),
                tuple(7, IdentityErrorCode.IMPORT_INVALID_VALUE),
                tuple(8, IdentityErrorCode.IMPORT_UNKNOWN_UNIT),
                tuple(9, IdentityErrorCode.IMPORT_REQUIRED_VALUE));
        assertThat(inTenant(() -> units.findTree())).hasSize(2);

        // With the other rows fixed, the remaining pair parents itself and is reported as a cycle.
        ImportReport cycle = inTenant(() -> service.importUnits(admin, file(List.of("a", "A", "b", ""),
                List.of("b", "B", "a", ""), List.of("c", "C", "", "")), true));
        assertThat(cycle.problems()).extracting(ImportProblem::row, ImportProblem::code).containsExactly(
                tuple(2, IdentityErrorCode.ORG_MOVE_CYCLE), tuple(3, IdentityErrorCode.ORG_MOVE_CYCLE));
        ImportReport duplicate = inTenant(() -> service.importUnits(admin, file(List.of("x", "X", "", ""),
                List.of("X", "Again", "", "")), false));
        assertThat(duplicate.problems()).extracting(ImportProblem::code).containsExactly(IdentityErrorCode.IMPORT_DUPLICATE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void refusesTooDeepTreesAndNonAdministrators()
    {
        List<List<String>> deep = new ArrayList<>();
        deep.add(OrgTransferService.COLUMNS);
        String parent = "sales";
        for (int level = 2; level <= OrgUnit.MAX_DEPTH + 1; level++) {
            deep.add(List.of("l" + level, "L" + level, parent, ""));
            parent = "l" + level;
        }
        deep.add(List.of("below", "Below", parent, ""));

        ImportReport report = inTenant(() -> service.importUnits(admin, deep, false));
        assertThat(report.problems()).extracting(ImportProblem::code)
                .containsExactly(IdentityErrorCode.ORG_TOO_DEEP, IdentityErrorCode.ORG_TOO_DEEP);
    }
}
