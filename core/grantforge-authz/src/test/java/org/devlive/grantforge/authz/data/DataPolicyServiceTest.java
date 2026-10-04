// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.application.AuthzErrorCode;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationEntity;
import org.devlive.grantforge.authz.domain.ApplicationEntityRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.DataPolicyRepository;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, DataPolicyService.class, DataEntities.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataPolicyServiceTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private DataPolicyService service;

    @Autowired
    private DataPolicyRepository policies;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ApplicationEntityRepository applicationEntities;

    private long platformTenant;
    private long acme;
    private long auditors;

    @BeforeEach
    void createRole()
    {
        platformTenant = tenants.save(Tenant.create("platform", "Platform").markPlatform()).requireId();
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        auditors = inAcme(() -> roles.save(Role.create("auditors", "Auditors", null)).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            policies.deleteAllInBatch();
            roles.deleteAllInBatch();
            units.deleteAllInBatch();
            return null;
        });
        events.deleteAllInBatch();
        tenants.deleteAllInBatch();
        applicationEntities.deleteAll();
        applications.deleteAllInBatch();
    }

    private <T> T inAcme(Supplier<T> action)
    {
        return TenantContext.callInTenant(acme, action::get);
    }

    private static JsonNode json(String text)
    {
        return JSON.readTree(text);
    }

    private static DataPolicyCommand command(DataScope scope)
    {
        return new DataPolicyCommand("user", DataAction.READ, scope, GrantEffect.ALLOW, null, List.of());
    }

    private static void assertRefused(Supplier<?> action, ErrorCode code, String... fields)
    {
        assertThatThrownBy(action::get).isInstanceOfSatisfying(GrantForgeException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(code);
            assertThat(error.getFieldIssues()).extracting(FieldIssue::field).containsExactlyInAnyOrder(fields);
        });
    }

    @Test
    void addsChangesAndRemovesDataPolicies()
    {
        DataPolicyView own = inAcme(() -> service.create(7, auditors, command(DataScope.SELF)));
        assertThat(own).extracting(DataPolicyView::roleId, DataPolicyView::entityCode, DataPolicyView::scope, DataPolicyView::condition)
                .containsExactly(auditors, "user", DataScope.SELF, null);
        DataPolicyView condition = inAcme(() -> service.create(7, auditors, new DataPolicyCommand("audit-event", DataAction.EXPORT,
                DataScope.CONDITION, GrantEffect.DENY, json("{\"field\": \"outcome\", \"op\": \"eq\", \"value\": \"FAILURE\"}"),
                List.of())));
        assertThat(condition.condition()).isEqualTo("{\"field\":\"outcome\",\"op\":\"eq\",\"value\":\"FAILURE\"}");
        long hq = inAcme(() -> units.save(OrgUnit.create(null, "hq", "HQ", 0)).requireId());
        long rd = inAcme(() -> units.save(OrgUnit.create(null, "rd", "R&D", 1)).requireId());
        DataPolicyView chosen = inAcme(() -> service.update(7, own.id(), new DataPolicyCommand("ignored", DataAction.UPDATE,
                DataScope.CUSTOM_ORGS, GrantEffect.ALLOW, null, List.of(rd, hq, rd))));
        assertThat(chosen).extracting(DataPolicyView::entityCode, DataPolicyView::action, DataPolicyView::scope)
                .containsExactly("user", DataAction.UPDATE, DataScope.CUSTOM_ORGS);
        assertThat(chosen.orgUnitIds()).containsExactlyInAnyOrder(hq, rd).hasSize(2);
        assertThat(inAcme(() -> service.list(auditors))).extracting(DataPolicyView::entityCode).containsExactly("audit-event", "user");

        inAcme(() -> {
            service.delete(7, condition.id());
            return null;
        });
        assertThat(inAcme(() -> service.list(auditors))).hasSize(1);
        assertThat(events.findAll()).extracting(event -> event.getAction().name()).containsExactlyInAnyOrder("DATA_POLICY_CREATED",
                "DATA_POLICY_CREATED", "DATA_POLICY_UPDATED", "DATA_POLICY_DELETED");
    }

    @Test
    void refusesPoliciesTheEntityOrTheTenantDoesNotAllow()
    {
        assertRefused(() -> inAcme(() -> service.create(7, auditors, new DataPolicyCommand("nothing", DataAction.READ, DataScope.TENANT,
                GrantEffect.ALLOW, null, List.of()))), AuthzErrorCode.DATA_POLICY_INVALID, "entityCode");
        // Groups belong to no one and no department.
        assertRefused(() -> inAcme(() -> service.create(7, auditors, new DataPolicyCommand("group", DataAction.READ, DataScope.SELF,
                GrantEffect.ALLOW, null, List.of()))), AuthzErrorCode.DATA_POLICY_INVALID, "scope");
        assertRefused(() -> inAcme(() -> service.create(7, auditors, command(DataScope.ALL))), AuthzErrorCode.DATA_POLICY_INVALID, "scope");
        long admins = TenantContext.callInTenant(platformTenant, () -> roles.save(Role.create("admins", "Admins", null)).requireId());
        assertThat(TenantContext.callInTenant(platformTenant, () -> service.create(7, admins, command(DataScope.ALL))).scope())
                .isEqualTo(DataScope.ALL);
        assertRefused(() -> inAcme(() -> service.create(7, auditors, command(DataScope.CONDITION))), AuthzErrorCode.DATA_POLICY_INVALID,
                "condition");
        assertRefused(() -> inAcme(() -> service.create(7, auditors, new DataPolicyCommand("user", DataAction.READ, DataScope.CONDITION,
                GrantEffect.ALLOW, json("{\"field\": \"passwordHash\", \"op\": \"eq\", \"value\": \"x\"}"), List.of()))),
                AuthzErrorCode.DATA_POLICY_INVALID, "condition.field");
        assertRefused(() -> inAcme(() -> service.create(7, auditors, new DataPolicyCommand("user", DataAction.READ, DataScope.TENANT,
                GrantEffect.ALLOW, json("{\"field\": \"status\", \"op\": \"eq\", \"value\": \"ACTIVE\"}"), List.of(1L)))),
                AuthzErrorCode.DATA_POLICY_INVALID, "condition", "orgUnitIds");
        assertRefused(() -> inAcme(() -> service.create(7, auditors, command(DataScope.CUSTOM_ORGS))), AuthzErrorCode.DATA_POLICY_INVALID,
                "orgUnitIds");
        assertRefused(() -> inAcme(() -> service.create(7, auditors, new DataPolicyCommand("user", DataAction.READ, DataScope.CUSTOM_ORGS,
                GrantEffect.ALLOW, null, List.of(424242L)))), AuthzErrorCode.DATA_POLICY_INVALID, "orgUnitIds");
        assertThat(inAcme(() -> service.create(7, auditors, new DataPolicyCommand("user", DataAction.READ, DataScope.TENANT,
                GrantEffect.ALLOW, JSON.nullNode(), List.of()))).condition()).isNull();
    }

    @Test
    void policiesBelongToRolesOfTheTenant()
    {
        assertRefused(() -> inAcme(() -> service.list(424242)), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> inAcme(() -> service.create(7, 424242, command(DataScope.TENANT))), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> inAcme(() -> service.update(7, 424242, command(DataScope.TENANT))), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> inAcme(() -> {
            service.delete(7, 424242);
            return null;
        }), CommonErrorCode.NOT_FOUND);
        assertRefused(() -> TenantContext.callInTenant(platformTenant, () -> service.list(auditors)), CommonErrorCode.NOT_FOUND);
    }

    @Test
    void checksPoliciesOnApplicationEntitiesAgainstTheirDeclaration()
    {
        long shop = applications.save(Application.create("shop", "Shop", null)).requireId();
        ApplicationEntity order = ApplicationEntity.create(shop, "shop:order");
        order.describe("Orders", true, false, List.of(new DataField("status", "Status", DataFieldType.CHOICE, List.of("OPEN", "PAID"))));
        applicationEntities.save(order);

        DataPolicyView paid = inAcme(() -> service.create(7, auditors, new DataPolicyCommand("shop:order", DataAction.READ,
                DataScope.CONDITION, GrantEffect.ALLOW, json("{\"field\": \"status\", \"op\": \"eq\", \"value\": \"PAID\"}"), List.of())));
        assertThat(paid.entityCode()).isEqualTo("shop:order");
        // Rows have an owner but no department, and conditions test declared fields only.
        assertRefused(() -> inAcme(() -> service.create(7, auditors, new DataPolicyCommand("shop:order", DataAction.READ, DataScope.ORG,
                GrantEffect.ALLOW, null, List.of()))), AuthzErrorCode.DATA_POLICY_INVALID, "scope");
        assertRefused(() -> inAcme(() -> service.create(7, auditors, new DataPolicyCommand("shop:order", DataAction.READ, DataScope.CONDITION,
                GrantEffect.ALLOW, json("{\"field\": \"total\", \"op\": \"gt\", \"value\": 1}"), List.of()))),
                AuthzErrorCode.DATA_POLICY_INVALID, "condition.field");
    }
}
