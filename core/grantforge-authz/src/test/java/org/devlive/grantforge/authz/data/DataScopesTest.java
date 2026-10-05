// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.application.AuthorizationEvaluator;
import org.devlive.grantforge.authz.application.AuthorizationVersions;
import org.devlive.grantforge.authz.application.EffectiveRoles;
import org.devlive.grantforge.authz.application.SubjectDirectory;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationEntity;
import org.devlive.grantforge.authz.domain.ApplicationEntityRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.DataPolicy;
import org.devlive.grantforge.authz.domain.DataPolicyRepository;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleParent;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.devlive.grantforge.persistence.secured.DataScope;
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

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, SubjectDirectory.class, EffectiveRoles.class, AuthorizationEvaluator.class,
        AuthorizationVersions.class, DataScopes.class, DataEntities.class, DataScopes.Sources.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataScopesTest
{
    @Autowired
    private DataScopes scopes;

    @Autowired
    private ScopedAccounts scopedAccounts;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleParentRepository parents;

    @Autowired
    private RoleAssignmentRepository assignments;

    @Autowired
    private DataPolicyRepository policies;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private OrgMemberRepository members;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private EntityManagerFactory factory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ApplicationEntityRepository applicationEntities;

    private DataScopeFixture data;

    @BeforeEach
    void createData()
    {
        data = new DataScopeFixture(tenants, accounts, units, members, events, factory, transactionManager);
    }

    @AfterEach
    void deleteData()
    {
        TenantContext.callAsSystem(() -> {
            policies.deleteAllInBatch();
            assignments.deleteAllInBatch();
            parents.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        applicationEntities.deleteAll();
        applications.deleteAllInBatch();
        data.delete();
    }

    private long role(String code)
    {
        return data.inAcme(() -> roles.save(Role.create(code, code, null)).requireId());
    }

    private void assign(long roleId, long accountId)
    {
        data.inAcme(() -> assignments.save(RoleAssignment.create(roleId, SubjectType.USER, accountId, RoleAssignment.Terms.UNLIMITED)));
    }

    private void policy(long roleId, String entity, DataAction action, DataScope scope, GrantEffect effect, @Nullable String condition)
    {
        data.inAcme(() -> {
            DataPolicy policy = DataPolicy.create(roleId, entity);
            policy.describe(action, scope, effect, condition, null);
            return policies.save(policy);
        });
    }

    private List<String> users(long accountId, DataAction action)
    {
        return data.find(UserAccount.class, data.inAcme(() -> scopes.scope(accountId, UserAccount.class, action)), UserAccount::getUsername);
    }

    @Test
    void combinesThePoliciesOfAllActiveRolesInheritedOnesIncluded()
    {
        long auditors = role("auditors");
        long seniors = role("seniors");
        data.inAcme(() -> parents.save(RoleParent.of(seniors, auditors)));
        assign(seniors, data.carol);
        policy(auditors, "user", DataAction.READ, DataScope.ORG_AND_CHILDREN, GrantEffect.ALLOW, null);
        policy(seniors, "user", DataAction.READ, DataScope.CONDITION, GrantEffect.ALLOW, "{\"field\":\"status\",\"op\":\"eq\",\"value\":\"DISABLED\"}");
        policy(seniors, "user", DataAction.READ, DataScope.SELF, GrantEffect.DENY, null);
        assertThat(users(data.carol, DataAction.READ)).containsExactly("alice", "dave");
        // Nothing allows exporting.
        assertThat(users(data.carol, DataAction.EXPORT)).isEmpty();
        // A reader without roles sees nothing.
        assertThat(users(data.bob, DataAction.READ)).isEmpty();

        // Disabled roles say nothing; changes apply to the next call.
        data.inAcme(() -> {
            Role senior = roles.findById(seniors).orElseThrow();
            senior.enable(false);
            return roles.save(senior);
        });
        assertThat(users(data.carol, DataAction.READ)).isEmpty();
    }

    @Test
    void systemRolesImplyTheirTenantOrEverything()
    {
        long admin = data.inAcme(() -> roles.save(Role.system(SystemRole.TENANT_ADMIN)).requireId());
        assign(admin, data.dave);
        assertThat(users(data.dave, DataAction.DELETE)).containsExactly("alice", "bob", "carol", "dave");
        DataAccess access = data.inAcme(() -> scopes.access(data.dave));
        assertThat(access.rules("user", DataAction.READ).allow()).containsExactly(DataRule.of(DataScope.TENANT));
        assertThat(data.inAcme(() -> scopes.access(data.dave))).isSameAs(access);
        assertThat(access.subject()).extracting(DataSubject::username, DataSubject::tenantId).containsExactly("dave", data.acme);

        long platform = data.inAcme(() -> roles.save(Role.system(SystemRole.PLATFORM_ADMIN)).requireId());
        assign(platform, data.bob);
        assertThat(data.inAcme(() -> scopes.access(data.bob)).rules("audit-event", DataAction.EXPORT).allow())
                .containsExactly(DataRule.of(DataScope.ALL));
        DataSubject bob = data.inAcme(() -> scopes.access(data.bob)).subject();
        assertThat(bob.orgUnitIds()).containsExactly(data.ops.requireId());
        assertThat(bob.orgUnitPaths()).containsExactly(data.ops.getPath());
    }

    @Test
    void conditionsThatNoLongerFitAllowNothingAndDenyEverything()
    {
        long auditors = role("auditors");
        assign(auditors, data.carol);
        policy(auditors, "user", DataAction.READ, DataScope.TENANT, GrantEffect.ALLOW, null);
        policy(auditors, "user", DataAction.READ, DataScope.CONDITION, GrantEffect.ALLOW, "{\"field\":\"gone\",\"op\":\"eq\",\"value\":1}");
        policy(auditors, "vanished", DataAction.READ, DataScope.TENANT, GrantEffect.ALLOW, null);
        assertThat(users(data.carol, DataAction.READ)).containsExactly("alice", "bob", "carol", "dave");
        policy(auditors, "user", DataAction.READ, DataScope.CONDITION, GrantEffect.DENY, "{\"field\":\"gone\",\"op\":\"eq\",\"value\":1}");
        assertThat(users(data.carol, DataAction.READ)).isEmpty();
    }

    @Test
    void chosenDepartmentsComeFromThePolicy()
    {
        long auditors = role("auditors");
        assign(auditors, data.carol);
        data.inAcme(() -> {
            DataPolicy policy = DataPolicy.create(auditors, "user");
            policy.describe(DataAction.UPDATE, DataScope.CUSTOM_ORGS, GrantEffect.ALLOW, null, "[" + data.ops.requireId() + "]");
            return policies.save(policy);
        });
        assertThat(users(data.carol, DataAction.UPDATE)).containsExactly("bob");
    }

    @Test
    void rowsOutsideTheScopeLookAbsent()
    {
        long auditors = role("auditors");
        assign(auditors, data.carol);
        policy(auditors, "user", DataAction.UPDATE, DataScope.ORG_AND_CHILDREN, GrantEffect.ALLOW, null);
        assertThat(data.inAcme(() -> scopes.requireWithin(data.carol, scopedAccounts, UserAccount.class, DataAction.UPDATE, data.alice))
                .getUsername()).isEqualTo("alice");
        assertThat(data.inAcme(() -> scopedAccounts.existsWithin(data.alice, scopes.scope(data.carol, UserAccount.class, DataAction.UPDATE))))
                .isTrue();
        assertThatThrownBy(() -> data.inAcme(() -> scopes.requireWithin(data.carol, scopedAccounts, UserAccount.class, DataAction.UPDATE,
                data.bob))).isInstanceOfSatisfying(GrantForgeException.class, error -> assertThat(error.getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> data.inAcme(() -> scopes.scope(data.carol, String.class, DataAction.READ)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> data.inAcme(() -> scopes.access(424242))).isInstanceOf(GrantForgeException.class);
    }

    @Test
    void previewsWhatARoleAloneWouldShow()
    {
        long auditors = role("auditors");
        long seniors = role("seniors");
        data.inAcme(() -> parents.save(RoleParent.of(seniors, auditors)));
        policy(auditors, "user", DataAction.READ, DataScope.ORG_AND_CHILDREN, GrantEffect.ALLOW, null);
        long viewers = role("viewers");
        assign(viewers, data.carol);
        policy(viewers, "user", DataAction.READ, DataScope.SELF, GrantEffect.ALLOW, null);
        // Carol sees herself now; with only the seniors role she would see her department tree, inherited from auditors.
        assertThat(data.inAcme(() -> scopes.preview(seniors, data.carol, "user", DataAction.READ))).isEqualTo(new DataPreview(2, 1));
        assertThat(data.inAcme(() -> scopes.preview(seniors, data.carol, "user", DataAction.DELETE))).isEqualTo(new DataPreview(0, 0));
        long admin = data.inAcme(() -> roles.save(Role.system(SystemRole.TENANT_ADMIN)).requireId());
        assertThat(data.inAcme(() -> scopes.preview(admin, data.carol, "audit-event", DataAction.READ)).withRole()).isEqualTo(2);
        assertThatThrownBy(() -> data.inAcme(() -> scopes.preview(seniors, data.carol, "nothing", DataAction.READ)))
                .isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> data.inAcme(() -> scopes.preview(424242, data.carol, "user", DataAction.READ)))
                .isInstanceOf(GrantForgeException.class);
    }

    @Test
    void givesAnApplicationTheRulesOfItsOwnEntitiesWithDepartmentsBelow()
    {
        long shop = applications.save(Application.create("shop", "Shop", null)).requireId();
        ApplicationEntity order = ApplicationEntity.create(shop, "shop:order");
        order.describe("Orders", true, true, List.of(new DataField("status", "Status", DataFieldType.CHOICE, List.of("OPEN", "PAID"))));
        applicationEntities.save(order);
        long buyers = role("buyers");
        assign(buyers, data.carol);
        policy(buyers, "shop:order", DataAction.READ, DataScope.ORG_AND_CHILDREN, GrantEffect.ALLOW, null);
        policy(buyers, "shop:order", DataAction.READ, DataScope.CONDITION, GrantEffect.DENY,
                "{\"field\": \"status\", \"op\": \"eq\", \"value\": \"PAID\"}");
        // Another application's entity of the same code stays out.
        policy(buyers, "user", DataAction.READ, DataScope.SELF, GrantEffect.ALLOW, null);

        ApplicationAccess access = data.inAcme(() -> scopes.accessIn(data.carol, "shop"));

        assertThat(access.rules()).containsOnlyKeys(new DataAccess.Key("order", DataAction.READ));
        DataAccess.Rules rules = requireNonNull(access.rules().get(new DataAccess.Key("order", DataAction.READ)));
        assertThat(rules.allow()).extracting(DataRule::scope).containsExactly(DataScope.ORG_AND_CHILDREN);
        assertThat(rules.deny()).singleElement().satisfies(rule -> assertThat(rule.condition()).isNotNull());
        assertThat(access.orgUnitsAndBelow()).containsExactlyInAnyOrder(data.hq.requireId(), data.rd.requireId());
        assertThat(access.subject().accountId()).isEqualTo(data.carol);
        // A reader whose roles say nothing about the application gets no rules, and GrantForge cannot count its rows.
        assertThat(data.inAcme(() -> scopes.accessIn(data.dave, "shop")).rules()).isEmpty();
        assertThatThrownBy(() -> data.inAcme(() -> scopes.preview(buyers, data.carol, "shop:order", DataAction.READ)))
                .isInstanceOfSatisfying(GrantForgeException.class, error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST));
    }
}
