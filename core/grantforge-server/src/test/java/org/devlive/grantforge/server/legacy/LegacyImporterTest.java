// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy;

import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.identity.application.PasswordService;
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.server.legacy.LegacyReport.Kind;
import org.devlive.grantforge.server.legacy.persistence.dialect.LegacyDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** The import of an AuthX 1.x database ({@code legacy/authx.sql}) into the assembled server. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:legacy-import")
class LegacyImporterTest
{
    /** The old database, kept for the whole run. */
    static final String SOURCE = "jdbc:h2:mem:legacy-source;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private static LegacyData data;

    @Autowired
    private LegacyImporter importer;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private UserAccountRepository accounts;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private RoleGrantRepository grants;
    @Autowired
    private RoleAssignmentRepository assignments;
    @Autowired
    private ApplicationRepository applications;
    @Autowired
    private ResourceRepository resources;
    @Autowired
    private PasswordService passwords;
    @Autowired
    private AuditEventRepository events;

    @BeforeAll
    static void loadSource() throws SQLException
    {
        try (Connection connection = DriverManager.getConnection(SOURCE); Statement statement = connection.createStatement()) {
            statement.execute("RUNSCRIPT FROM 'classpath:/legacy/authx.sql' CHARSET 'UTF-8'");
            data = LegacyDatabase.read(connection);
        }
    }

    private long tenant(String code)
    {
        return tenants.save(Tenant.create(code, code)).requireId();
    }

    private <T> T in(long tenant, Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, action);
    }

    @Test
    void importsOnceAndThenOnlyLinks()
    {
        long target = tenant("legacy-target");
        long other = tenant("legacy-other");
        TenantContext.runInTenant(other, () -> accounts.save(UserAccount.create("taken", "{noop}x", Instant.now())));
        TenantContext.runInTenant(target, () -> roles.save(Role.system(SystemRole.TENANT_ADMIN)));

        LegacyReport dryRun = importer.run(data, "legacy-target", "legacy", false, "h2:mem");
        assertCreated(dryRun, 2, 4, 6, 3, 8, 2);
        assertThat(in(target, () -> accounts.findByUsernameNorm("admin"))).isEmpty();
        assertThat(applications.findByCode("legacy")).isEmpty();

        LegacyReport applied = importer.run(data, "legacy-target", "legacy", true, "h2:mem");
        assertCreated(applied, 2, 4, 6, 3, 8, 2);
        Map<String, String> skipped = applied.issues().stream().filter(issue -> LegacyReport.SKIPPED.equals(issue.outcome()))
                .collect(Collectors.toMap(issue -> issue.kind() + ":" + issue.label(), LegacyReport.Issue::reason, (first, second) -> first));
        assertThat(skipped).containsKeys("USERS:系统用户", "USERS:Admin", "USERS:nopass", "USERS:taken", "ROLES:系统", "MENUS:更深", "MENUS:环一",
                "MENUS:环二", "GRANTS:role 1 → menu 61", "GRANTS:role 5 → menu 3", "ASSIGNMENTS:user 1 → role 1", "ASSIGNMENTS:user 6 → role 1");
        assertThat(skipped).hasSize(12);
        assertThat(applied.wildcards()).containsExactly(new LegacyReport.Wildcard(9, "/api/v1/user/info/*", "/api/v1/user/info/**"));

        // Accounts keep their passwords, whatever the digest's case, and their state.
        UserAccount admin = in(target, () -> accounts.findByUsernameNorm("admin")).orElseThrow();
        assertThat(admin.getId()).isEqualTo(applied.newId(Kind.USERS, 2));
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
        assertThat(passwords.verify(admin, "admin-password")).isTrue();
        UserAccount user = in(target, () -> accounts.findByUsernameNorm("user")).orElseThrow();
        assertThat(passwords.verify(user, "user-password")).isTrue();
        assertThat(user.getStatus()).isEqualTo(AccountStatus.DISABLED);
        assertThat(user.getLockedUntil()).isEqualTo(UserAccount.LOCKED_INDEFINITELY);
        assertThat(user.getEmail()).isNull();

        // Roles get valid codes; an inactive one stays disabled.
        assertThat(in(target, () -> roles.findByCodeIn(List.of("gly", "pt-yh", "legacy-role-3", "gly-4")))).extracting(Role::getCode)
                .containsExactlyInAnyOrder("gly", "pt-yh", "legacy-role-3", "gly-4");
        assertThat(in(target, () -> roles.findByCode("pt-yh")).orElseThrow().isEnabled()).isFalse();

        // Menus become a tree of menus, pages and buttons; APIs of the old menus stay out of the navigation.
        long application = applications.findByCode("legacy").map(Application::requireId).orElseThrow();
        Map<String, Resource> tree = in(target, () -> resources.findTree(application)).stream()
                .collect(Collectors.toMap(Resource::getCode, resource -> resource));
        assertThat(tree).containsOnlyKeys("menu-2", "menu-3", "menu-8", "menu-9", "menu-40", "menu-50", "api:GET:/admin/menus",
                "api:GET:/api/v1/user/info/**", "api:POST:/api/v1/user/info/**");
        assertThat(requireNonNull(tree.get("menu-2")).getType()).isEqualTo(ResourceType.MENU);
        assertThat(requireNonNull(tree.get("menu-3")).getType()).isEqualTo(ResourceType.PAGE);
        assertThat(requireNonNull(tree.get("menu-3")).getDetails().route()).isEqualTo("/admin/menus");
        assertThat(requireNonNull(tree.get("menu-3")).getParentId()).isEqualTo(requireNonNull(tree.get("menu-2")).getId());
        assertThat(requireNonNull(tree.get("menu-40")).getType()).isEqualTo(ResourceType.ACTION);
        assertThat(requireNonNull(tree.get("menu-9")).getDetails().visible()).isFalse();
        assertThat(requireNonNull(tree.get("menu-9")).getDetails().route()).isNull();
        assertThat(requireNonNull(tree.get("menu-9")).isEnabled()).isFalse();
        assertThat(requireNonNull(tree.get("menu-50")).getParentId()).isNull();

        // The administrator role holds its menus and their APIs; accounts hold their roles.
        long gly = in(target, () -> roles.findByCode("gly")).map(Role::requireId).orElseThrow();
        assertThat(in(target, () -> grants.findByRoleId(gly))).hasSize(6);
        assertThat(in(target, () -> assignments.findByRoleIdAndSubjectTypeAndSubjectId(gly, SubjectType.USER, admin.requireId()))).isPresent();
        List<AuditEvent> audited = TenantContext.callAsSystem(() -> events.findAll());
        assertThat(audited).extracting(AuditEvent::getAction).containsOnlyOnce(AuditAction.LEGACY_IMPORTED);

        LegacyReport again = importer.run(data, "legacy-target", "legacy", true, "h2:mem");
        assertCreated(again, 0, 0, 0, 0, 0, 0);
        assertThat(again.existingCount(Kind.USERS)).isEqualTo(2);
        assertThat(again.existingCount(Kind.ROLES)).isEqualTo(4);
        assertThat(again.existingCount(Kind.MENUS)).isEqualTo(6);
        assertThat(again.existingCount(Kind.GRANTS)).isEqualTo(8);
        assertThat(again.existingCount(Kind.ASSIGNMENTS)).isEqualTo(2);
        assertThat(again.newId(Kind.USERS, 2)).isEqualTo(admin.getId());
    }

    private static void assertCreated(LegacyReport report, int users, int roles, int menus, int apis, int grants, int assignments)
    {
        assertThat(List.of(report.createdCount(Kind.USERS), report.createdCount(Kind.ROLES), report.createdCount(Kind.MENUS),
                report.createdCount(Kind.APIS), report.createdCount(Kind.GRANTS), report.createdCount(Kind.ASSIGNMENTS)))
                .containsExactly(users, roles, menus, apis, grants, assignments);
    }

    @Test
    void refusesAnUnknownTenant()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> importer.run(data, "no-such-tenant", "legacy", false, "h2:mem"))
                .withMessageContaining("no-such-tenant");
    }

    @Test
    void wildcardsBecomeSegmentPatterns()
    {
        assertThat(LegacyImporter.pattern("/api/v1/user/info/*")).isEqualTo("/api/v1/user/info/**");
        assertThat(LegacyImporter.pattern("/api/v1/user*")).isEqualTo("/api/v1/user/**");
        assertThat(LegacyImporter.pattern(" api/v1/users ")).isEqualTo("/api/v1/users");
    }
}
