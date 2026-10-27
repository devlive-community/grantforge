// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.datasource;

import org.devlive.grantforge.authz.application.EffectiveRoles;
import org.devlive.grantforge.authz.domain.AuthorizationVersionRepository;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.datasource.application.DataSourceCommand;
import org.devlive.grantforge.datasource.application.DataSourceService;
import org.devlive.grantforge.datasource.application.DirectoryReport;
import org.devlive.grantforge.datasource.application.DirectoryWriter;
import org.devlive.grantforge.datasource.domain.DataSourceRepository;
import org.devlive.grantforge.datasource.domain.DirectoryAccountRepository;
import org.devlive.grantforge.datasource.domain.DirectoryDepartmentRepository;
import org.devlive.grantforge.datasource.provider.DirectoryProvider;
import org.devlive.grantforge.datasource.provider.dingtalk.DingTalkDirectory;
import org.devlive.grantforge.identity.application.AuthenticationService;
import org.devlive.grantforge.identity.application.DirectoryUser;
import org.devlive.grantforge.identity.application.IdentitySourceCommand;
import org.devlive.grantforge.identity.application.IdentitySourceService;
import org.devlive.grantforge.identity.application.LoginSucceeded;
import org.devlive.grantforge.identity.application.OidcSettings;
import org.devlive.grantforge.identity.application.OrgService;
import org.devlive.grantforge.identity.application.UserAdminService;
import org.devlive.grantforge.identity.application.UserProfileInput;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.devlive.grantforge.identity.domain.OrgMember;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.authz.AuthorizationChanges;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:data-source-integration")
@Import(DataSourceIntegrationTest.OtherProvider.class)
class DataSourceIntegrationTest
{
    @MockitoSpyBean
    private DingTalkDirectory directory;
    @MockitoSpyBean
    private DirectoryWriter writer;
    @Autowired
    private DataSourceService data;
    @Autowired
    private DataSourceRepository sources;
    @Autowired
    private IdentitySourceService identities;
    @Autowired
    private AuthenticationService authentication;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private DirectoryAccountRepository bindings;
    @Autowired
    private DirectoryDepartmentRepository mappings;
    @Autowired
    private OrgUnitRepository units;
    @Autowired
    private OrgMemberRepository members;
    @Autowired
    private OrgService org;
    @Autowired
    private UserAdminService userAdmin;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private AuthorizationVersionRepository versions;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private RoleAssignmentRepository assignments;
    @Autowired
    private EffectiveRoles effective;

    private long tenant;
    private long identity;
    private long source;
    private String code = "";

    @BeforeEach
    void prepare()
    {
        tenant = tenants.save(Tenant.create("t-" + UUID.randomUUID(), "Test")).requireId();
        code = "sso-" + tenant;
        identity = inTenant(() -> identities.create(1, new IdentitySourceCommand(code, "Internal SSO", IdentitySourceType.OIDC, true, true, null,
            new OidcSettings("https://sso.example.com", "client", "", "", "", ""), null, null))).id();
        source = Long.parseLong(inTenant(() -> data.create(1, command(true, "sdk-secret"))).id());
        snapshot(List.of(employee("u1", "alice@example.com", "10", "20")));
    }

    @Test
    void sourceNamesFollowConfigurationAndRemainTenantScoped()
    {
        sync();
        Map<String, Long> ids = departments();
        assertThat(inTenant(() -> data.departmentSourceNames(ids.values())))
            .containsEntry(ids.get("10"), "Organization").containsEntry(ids.get("20"), "Organization");
        assertThat(inTenant(() -> data.departmentSourceNames(List.of()))).isEmpty();

        DataSourceCommand renamed = new DataSourceCommand("corp", "DingTalk", "DINGTALK", identity, true, true,
            Map.of("corpId", "corp", "appKey", "app", "rootDepartmentId", "1"), null, 30, null);
        inTenant(() -> data.update(1, source, renamed));
        assertThat(inTenant(() -> data.departmentSourceNames(ids.values()))).containsEntry(ids.get("10"), "DingTalk");

        long otherTenant = tenants.save(Tenant.create("t-" + UUID.randomUUID(), "Other")).requireId();
        assertThat(TenantContext.callInTenant(otherTenant, () -> data.departmentSourceNames(ids.values()))).isEmpty();
    }

    @Test
    void firstSyncAndLoginFillMembershipsWithoutRemoteLoginCalls()
    {
        assertThat(sync().departmentsCreated()).isEqualTo(3);
        long account = login("a", " ALICE@example.com ");
        assertThat(inTenant(() -> members.findByAccount(account))).hasSize(2).filteredOn(OrgMember::isPrimaryUnit).hasSize(1);
        var rows = inTenant(() -> members.findByAccount(account));
        long version = version();
        fire(account, "alice@example.com");
        assertThat(inTenant(() -> members.findByAccount(account))).extracting(OrgMember::requireId)
            .containsExactlyElementsOf(rows.stream().map(OrgMember::requireId).toList());
        assertThat(version()).isEqualTo(version);
        verify(directory).fetch(anyMap(), anyString());
        assertThat(inTenant(() -> identities.get(identity)).oidc()).isEqualTo(new OidcSettings("https://sso.example.com", "client", "", "", "", ""));
    }

    @Test
    void loginBeforeFirstSyncIsLinkedAfterSnapshotArrives()
    {
        long account = login("a", "alice@example.com");
        assertThat(inTenant(() -> members.findByAccount(account))).isEmpty();
        transaction(() -> users.findById(account).orElseThrow().withEmail("edited@example.com"));
        assertThat(sync().linked()).isEqualTo(1);
        assertThat(inTenant(() -> members.findByAccount(account))).hasSize(2).filteredOn(OrgMember::isPrimaryUnit).hasSize(1);
    }

    @Test
    void repeatedLoginRepairsMissingMembershipsAndPrimary()
    {
        sync();
        long account = login("a", "alice@example.com");
        transaction(() -> {
            members.deleteAll(members.findByAccount(account));
            return true;
        });
        fire(account, "alice@example.com");
        assertThat(inTenant(() -> members.findByAccount(account))).hasSize(2).filteredOn(OrgMember::isPrimaryUnit).hasSize(1);
        transaction(() -> {
            var old = members.findByAccount(account);
            members.deleteAll(old);
            members.flush();
            old.forEach(member -> members.save(OrgMember.of(account, member.getOrgUnitId(), false)));
            return true;
        });
        fire(account, "alice@example.com");
        assertThat(inTenant(() -> members.findByAccount(account))).filteredOn(OrgMember::isPrimaryUnit).hasSize(1);
    }

    @Test
    void profileMailAndDuplicateMailboxesNeverEstablishBindings()
    {
        long first = login("a", "alice@example.com");
        long second = login("b", "alice@example.com");
        long duplicate = login("c", "duplicate@example.com");
        long edited = login("d", "absent@example.com");
        transaction(() -> users.findById(edited).orElseThrow().withEmail("alice@example.com"));
        snapshot(List.of(employee("u1", "alice@example.com", "10"), employee("u2", "duplicate@example.com", "10"), employee("u3", "duplicate@example.com", "20")));
        sync();
        for (long account : List.of(first, second, duplicate, edited)) {
            assertThat(inTenant(() -> members.findByAccount(account))).isEmpty();
        }
    }

    @Test
    void stableBindingAndLocalPrimarySurviveMailReuseAndEmployeeDeparture()
    {
        sync();
        long account = login("a", "alice@example.com");
        long local = transaction(() -> {
            long id = units.save(OrgUnit.create(null, "local", "Local", 0)).requireId();
            var current = members.findByAccount(account);
            members.deleteAll(current);
            members.flush();
            current.forEach(member -> members.save(OrgMember.of(account, member.getOrgUnitId(), false)));
            members.save(OrgMember.of(account, id, true));
            return id;
        });
        snapshot(List.of(employee("u1", "changed@example.com", "20"), employee("u2", "alice@example.com", "10")));
        sync();
        fire(account, "alice@example.com");
        assertThat(inTenant(() -> bindings.findBySourceId(source))).singleElement().extracting(link -> link.getEmployeeId()).isEqualTo("u1");
        assertThat(inTenant(() -> members.findByAccount(account))).extracting(OrgMember::getOrgUnitId).containsExactlyInAnyOrder(local, departments().get("20"));
        assertThat(inTenant(() -> members.findByAccount(account))).filteredOn(OrgMember::isPrimaryUnit).singleElement().extracting(OrgMember::getOrgUnitId).isEqualTo(local);
        snapshot(List.of(employee("u2", "alice@example.com", "10")));
        sync();
        assertThat(inTenant(() -> members.findByAccount(account))).extracting(OrgMember::getOrgUnitId).containsExactly(local);
    }

    @Test
    void failedOrIncompleteSnapshotsRetainThePreviousDirectory()
    {
        sync();
        long account = login("a", "alice@example.com");
        var before = inTenant(() -> members.findByAccount(account));
        doThrow(new IllegalStateException("unavailable")).when(directory).fetch(anyMap(), anyString());
        assertThatThrownBy(this::sync).isInstanceOf(IllegalStateException.class);
        assertThat(inTenant(() -> members.findByAccount(account))).extracting(OrgMember::requireId).containsExactlyElementsOf(before.stream().map(OrgMember::requireId).toList());
        doReturn(new DirectoryProvider.Snapshot(tree(), List.of(), "changed-scope")).when(directory).fetch(anyMap(), anyString());
        assertThatThrownBy(this::sync).isInstanceOf(GrantForgeException.class);
        doReturn(new DirectoryProvider.Snapshot(List.of(new DirectoryProvider.Department("1", null, "Changed", 0),
            new DirectoryProvider.Department("bad", "1", "x".repeat(129), 0)), List.of(), "scope")).when(directory).fetch(anyMap(), anyString());
        assertThatThrownBy(this::sync).isInstanceOf(GrantForgeException.class);
        assertThat(inTenant(() -> units.findById(departments().get("1")).orElseThrow().getName())).isEqualTo("Company");
    }

    @Test
    void externalOwnershipIsGenericAndKeepsDepartmentIdsAcrossMoves()
    {
        sync();
        Map<String, Long> before = departments();
        doReturn(new DirectoryProvider.Snapshot(List.of(new DirectoryProvider.Department("1", null, "Company", 0),
            new DirectoryProvider.Department("20", "1", "Finance", 0), new DirectoryProvider.Department("10", "20", "Renamed", 0)),
            List.of(employee("u1", "alice@example.com", "10")), "scope")).when(directory).fetch(anyMap(), anyString());
        sync();
        assertThat(departments()).isEqualTo(before);
        var child = inTenant(() -> units.findById(before.get("10")).orElseThrow());
        assertThat(child.getDepth()).isEqualTo(2);
        long administrator = transaction(() -> {
            long actor = users.save(UserAccount.create("manager" + tenant, "unused", Instant.now())).requireId();
            long role = roles.save(Role.system(SystemRole.TENANT_ADMIN)).requireId();
            assignments.save(RoleAssignment.create(role, SubjectType.USER, actor, new RoleAssignment.Terms(null, null, false)));
            return actor;
        });
        assertThatThrownBy(() -> inTenant(() -> org.update(administrator, child.requireId(), "edited", "Edited")))
            .isInstanceOf(GrantForgeException.class).hasMessageContaining("managed by an external directory");
        long account = login("a", "alice@example.com");
        assertThatThrownBy(() -> inTenant(() -> userAdmin.update(administrator, account, new UserProfileInput("Alice", "alice@example.com", null, List.of(), List.of()))))
            .isInstanceOf(GrantForgeException.class).hasMessageContaining("managed by an external directory");
    }

    @Test
    void configuringAMountRespectsExistingOrganizationDataPermissions()
    {
        long hidden = transaction(() -> units.save(OrgUnit.create(null, "hidden", "Hidden", 0)).requireId());
        var mounted = new DataSourceCommand("corp", "Organization", "DINGTALK", identity, true, true,
            Map.of("corpId", "corp", "appKey", "app", "rootDepartmentId", "1"), hidden, 30, null);
        assertThatThrownBy(() -> inTenant(() -> data.update(1, source, mounted))).isInstanceOf(GrantForgeException.class);
        assertThat(inTenant(() -> data.list().get(0).parentUnitId())).isNull();
    }

    @Test
    void credentialsAreSealedAndPendingBindingsPreventRetargeting()
    {
        assertThat(inTenant(() -> sources.findById(source).orElseThrow().getSecret())).isNotEqualTo("sdk-secret");
        login("a", "alice@example.com");
        var changed = new DataSourceCommand("corp", "Changed", "DINGTALK", identity, true, true,
            Map.of("corpId", "other", "appKey", "app", "rootDepartmentId", "1"), null, 30, null);
        assertThatThrownBy(() -> inTenant(() -> data.update(1, source, changed))).isInstanceOf(GrantForgeException.class);
        transaction(() -> {
            sources.lockById(source).orElseThrow().claimSync("other", Instant.now().plusSeconds(60));
            return true;
        });
        assertThatThrownBy(this::sync).isInstanceOf(GrantForgeException.class);
    }

    @Test
    void disabledDataSourcesDoNotRejectOidcOrGrantMemberships()
    {
        sync();
        inTenant(() -> data.update(1, source, command(false, null)));
        long account = login("a", "alice@example.com");
        assertThat(inTenant(() -> members.findByAccount(account))).isEmpty();
        assertThat(inTenant(() -> identities.get(identity)).enabled()).isTrue();
        assertThatThrownBy(this::sync).isInstanceOf(GrantForgeException.class);
    }

    @Test
    void directoryWriteFailureCannotRejectAnAuthenticatedLogin()
    {
        sync();
        long account = login("a", "alice@example.com");
        var before = inTenant(() -> members.findByAccount(account));
        doThrow(new IllegalStateException("simulated persistence failure")).when(writer).login(any(), eq(account), anyString());
        fire(account, "alice@example.com");
        assertThat(inTenant(() -> members.findByAccount(account))).extracting(OrgMember::requireId)
            .containsExactlyElementsOf(before.stream().map(OrgMember::requireId).toList());
    }

    @Test
    void loginContextCannotCrossSourcesOrTenants()
    {
        sync();
        long account = authentication.signInFederated(code, new DirectoryUser("sub-a", "a" + tenant, "Alice", "alice@example.com")).accountId();
        events.publishEvent(new LoginSucceeded(tenant, account, new LoginSucceeded.Federated("different-source", "alice@example.com")));
        long other = tenants.save(Tenant.create("other-" + UUID.randomUUID(), "Other")).requireId();
        events.publishEvent(new LoginSucceeded(other, account, new LoginSucceeded.Federated(code, "alice@example.com")));
        assertThat(inTenant(() -> members.findByAccount(account))).isEmpty();
    }

    @Test
    void organizationRolesAndPermissionVersionsFollowActualMembershipChanges()
    {
        sync();
        long department = departments().get("10");
        long role = transaction(() -> {
            long id = roles.save(Role.create("engineer", "Engineer", null)).requireId();
            assignments.save(RoleAssignment.create(id, SubjectType.ORG_UNIT, department, new RoleAssignment.Terms(null, null, true)));
            return id;
        });
        long account = login("a", "alice@example.com");
        assertThat(transaction(() -> effective.of(account, Instant.now()))).anyMatch(item -> item.role().id() == role && item.active());
        long before = version();
        snapshot(List.of(employee("u1", "alice@example.com", "20")));
        sync();
        assertThat(version()).isGreaterThan(before);
        assertThat(transaction(() -> effective.of(account, Instant.now()))).noneMatch(item -> item.role().id() == role && item.active());
        long unchanged = version();
        sync();
        assertThat(version()).isEqualTo(unchanged);
    }

    @Test
    void anotherProviderUsesTheSameWriterWithStringIdentifiers()
    {
        inTenant(() -> {
            data.delete(1, source);
            return true;
        });
        var other = inTenant(() -> data.create(1, new DataSourceCommand("other", "Other provider", "OTHER", identity, true, true, Map.of(), null, null, "secret")));
        var report = inTenant(() -> data.sync(1L, Long.parseLong(other.id())));
        assertThat(report.departmentsCreated()).isEqualTo(1);
        assertThat(inTenant(() -> mappings.findBySourceId(Long.parseLong(other.id())))).singleElement().extracting(mapping -> mapping.getExternalId()).isEqualTo("department-root");
        verify(directory, never()).fetch(anyMap(), anyString());
    }

    private DataSourceCommand command(boolean enabled, @Nullable String secret)
    {
        return new DataSourceCommand("corp", "Organization", "DINGTALK", identity, enabled, true,
            Map.of("corpId", "corp", "appKey", "app", "rootDepartmentId", "1"), null, 30, secret == null && source == 0 ? "sdk-secret" : secret);
    }

    private DirectoryReport sync()
    {
        return inTenant(() -> data.sync(1L, source));
    }

    private long login(String sub, String email)
    {
        long account = authentication.signInFederated(code, new DirectoryUser("sub-" + sub, sub + tenant, sub, email)).accountId();
        fire(account, email);
        return account;
    }

    private void fire(long account, String email)
    {
        events.publishEvent(new LoginSucceeded(tenant, account, new LoginSucceeded.Federated(code, email)));
    }

    private void snapshot(List<DirectoryProvider.Employee> employees)
    {
        doReturn(new DirectoryProvider.Snapshot(tree(), employees, "scope")).when(directory).fetch(anyMap(), anyString());
    }

    private static List<DirectoryProvider.Department> tree()
    {
        return List.of(new DirectoryProvider.Department("1", null, "Company", 0), new DirectoryProvider.Department("10", "1", "Engineering", 0),
            new DirectoryProvider.Department("20", "1", "Finance", 1));
    }

    private static DirectoryProvider.Employee employee(String id, String email, String... departments)
    {
        return new DirectoryProvider.Employee(id, email, List.of(departments));
    }

    private Map<String, Long> departments()
    {
        return inTenant(() -> mappings.findBySourceId(source)).stream().collect(Collectors.toMap(mapping -> mapping.getExternalId(), mapping -> mapping.getOrgUnitId()));
    }

    private long version()
    {
        return versions.findById(AuthorizationChanges.tenantScope(tenant)).orElseThrow().getVersion();
    }

    private <T> T inTenant(Supplier<T> task)
    {
        return TenantContext.callInTenant(tenant, task);
    }

    private <T> T transaction(Supplier<T> task)
    {
        return inTenant(() -> requireNonNull(new TransactionTemplate(transactions).execute(status -> task.get())));
    }

    @TestConfiguration
    static class OtherProvider
    {
        @Bean
        DirectoryProvider exampleProvider()
        {
            return new DirectoryProvider()
            {
                public String type()
                {
                    return "OTHER";
                }

                public Map<String, String> validate(Map<String, String> settings)
                {
                    return settings;
                }

                public Snapshot fetch(Map<String, String> settings, String secret)
                {
                    return new Snapshot(List.of(new Department("department-root", null, "Other company", 0)), List.of(), "scope");
                }
            };
        }
    }
}
