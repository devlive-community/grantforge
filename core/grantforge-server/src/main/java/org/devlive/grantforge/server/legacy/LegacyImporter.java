// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.query.InClauseBatcher;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.server.legacy.LegacyReport.Kind;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Imports the old database into a tenant (D-80): accounts keep their passwords, which the first sign-in rehashes; roles
 * keep their names with codes made valid; menus become resources of a catalog application, their URLs with HTTP methods
 * API resources; and roles' menus and accounts' roles become grants and assignments. Only explicit grants come over:
 * the old server let anyone reach URLs registered as menus, which GrantForge never does.
 *
 * <p>What exists already is kept as it is and only linked (accounts by login name, roles by code, resources by code),
 * so an import can run again. Everything happens in one transaction; a dry run takes the same steps and rolls them
 * back, so its report is what applying would do.
 */
@Service
// Creating an object for each old row is what an import does.
@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
public final class LegacyImporter
{
    /** Who granted what the import grants: no account, the import itself. */
    static final long IMPORT = 0;

    private static final Pattern LEGACY_HASH = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern ROLE_CODE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Pattern ROUTE = Pattern.compile("/\\S{0,254}");
    private static final String FOLDER = "#";
    private static final String API_PREFIX = "/api/";

    private final TenantRepository tenants;
    private final UserAccountRepository accounts;
    private final RoleRepository roles;
    private final RoleAssignmentRepository assignments;
    private final RoleGrantRepository grants;
    private final ApplicationRepository applications;
    private final ResourceRepository resources;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the importer.
     *
     * @param tenants the tenants
     * @param accounts the accounts
     * @param roles the roles
     * @param assignments the role assignments
     * @param grants the role grants
     * @param applications the catalog applications
     * @param resources the resources
     * @param audit records the import
     * @param transactionManager opens the transaction
     * @param clock the current time
     */
    public LegacyImporter(TenantRepository tenants, UserAccountRepository accounts, RoleRepository roles, RoleAssignmentRepository assignments,
            RoleGrantRepository grants, ApplicationRepository applications, ResourceRepository resources, AuditLog audit,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.tenants = requireNonNull(tenants, "tenants");
        this.accounts = requireNonNull(accounts, "accounts");
        this.roles = requireNonNull(roles, "roles");
        this.assignments = requireNonNull(assignments, "assignments");
        this.grants = requireNonNull(grants, "grants");
        this.applications = requireNonNull(applications, "applications");
        this.resources = requireNonNull(resources, "resources");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Imports the old data, or only works out what importing would do.
     *
     * @param data what the old database holds
     * @param tenantCode the tenant to fill
     * @param applicationCode the catalog application for the menus; created if it does not exist
     * @param apply {@code true} to write, {@code false} for a dry run
     * @param source the old database, without credentials, for the report
     * @return what was or would be done
     * @throws IllegalArgumentException for an unknown tenant or an application code that is not valid
     */
    public LegacyReport run(LegacyData data, String tenantCode, String applicationCode, boolean apply, String source)
    {
        requireNonNull(data, "data");
        Tenant tenant = tenants.findByCode(requireNonNull(tenantCode, "tenantCode"))
                .orElseThrow(() -> new IllegalArgumentException("no tenant with the code " + tenantCode));
        long tenantId = tenant.requireId();
        LegacyReport report = new LegacyReport(apply, source, tenantCode, applicationCode);
        // Login names are unique across tenants; looked up before the tenant's transaction binds its filter.
        Map<String, UserAccount> taken = new HashMap<>();
        Set<String> names = data.users().stream().map(user -> UserAccount.normalize(user.name())).filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
        TenantContext.callAsSystem(() -> InClauseBatcher.query(names, accounts::findByUsernameNormIn))
                .forEach(account -> taken.put(account.getUsernameNorm(), account));
        TenantContext.runInTenant(tenantId, () -> transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            Map<Long, Long> accountIds = users(data, tenantId, taken, report, now);
            Map<Long, Long> roleIds = roles(data, report);
            Application application = application(applicationCode);
            Map<Long, Resource> menus = menus(data, application.requireId(), report);
            Map<Long, List<Resource>> apis = apis(data, application.requireId(), menus, report);
            grant(data, roleIds, menus, apis, report);
            assign(data, accountIds, roleIds, report);
            audit.recordWithChange(new AuditRecord(AuditAction.LEGACY_IMPORTED, AuditOutcome.SUCCESS, tenantId, null, null, applicationCode,
                    report.createdCount() + " created, " + report.skippedCount() + " left out"));
            if (!apply) {
                status.setRollbackOnly();
            }
        }));
        return report;
    }

    /** Accounts: a valid, free login name and a legacy digest are required; the first sign-in rehashes the password. */
    private Map<Long, Long> users(LegacyData data, long tenantId, Map<String, UserAccount> taken, LegacyReport report, Instant now)
    {
        Map<Long, Long> ids = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (LegacyData.User user : data.users()) {
            String name = Strings.blankToNull(user.name());
            String password = Strings.blankToNull(user.password());
            if (name == null || !UserAccount.USERNAME.matcher(name).matches()) {
                report.skipped(Kind.USERS, user.id(), user.name(), "not a valid login name (3-64 letters, digits or ._@-)");
                continue;
            }
            String normalized = UserAccount.normalize(name);
            if (!seen.add(normalized)) {
                report.skipped(Kind.USERS, user.id(), name, "another account of the old database has the same login name");
                continue;
            }
            UserAccount existing = taken.get(normalized);
            if (existing != null) {
                if (!Long.valueOf(tenantId).equals(existing.getTenantId())) {
                    report.skipped(Kind.USERS, user.id(), name, "the login name is taken in another tenant");
                    continue;
                }
                ids.put(user.id(), existing.requireId());
                report.mapped(Kind.USERS, user.id(), existing.requireId());
                report.existing(Kind.USERS);
                continue;
            }
            if (password == null || !LEGACY_HASH.matcher(password).matches()) {
                report.skipped(Kind.USERS, user.id(), name, "no SHA-256 password digest, so the account could never sign in");
                continue;
            }
            UserAccount account = UserAccount.create(name, "{sha256-legacy}" + password.toLowerCase(Locale.ROOT), now).withDisplayName(name);
            String email = Strings.blankToNull(user.email());
            if (email != null && email.length() <= UserAccount.MAX_EMAIL && UserAccount.EMAIL.matcher(email).matches()) {
                account.withEmail(email);
            }
            else if (email != null) {
                report.noted(Kind.USERS, user.id(), name, "the e-mail address \"" + email + "\" is not valid and was left out");
            }
            if (!user.active()) {
                account.disable();
            }
            if (user.locked()) {
                account.lockIndefinitely();
            }
            if (user.system()) {
                report.noted(Kind.USERS, user.id(), name, "was a system account; imported as an ordinary one");
            }
            long id = accounts.saveAndFlush(account).requireId();
            ids.put(user.id(), id);
            report.mapped(Kind.USERS, user.id(), id);
            report.created(Kind.USERS);
        }
        return ids;
    }

    /** Roles: codes become lowercase and valid; a code of a system role is never taken over. */
    private Map<Long, Long> roles(LegacyData data, LegacyReport report)
    {
        Map<Long, Long> ids = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (LegacyData.Role legacy : data.roles()) {
            String code = roleCode(legacy, used);
            String name = Strings.blankToNull(legacy.name());
            Role existing = roles.findByCode(code).orElse(null);
            if (existing != null && existing.getType() == RoleType.SYSTEM) {
                report.skipped(Kind.ROLES, legacy.id(), legacy.name(), "its code " + code + " is a system role's");
                continue;
            }
            if (existing != null) {
                ids.put(legacy.id(), existing.requireId());
                report.mapped(Kind.ROLES, legacy.id(), existing.requireId());
                report.existing(Kind.ROLES);
                continue;
            }
            String description = Strings.blankToNull(legacy.description());
            Role role = Role.create(code, cut(name == null ? code : name, Role.NAME_MAX),
                    description == null ? null : cut(description, Role.DESCRIPTION_MAX));
            role.enable(legacy.active());
            if (!code.equals(Strings.blankToNull(legacy.code()))) {
                report.noted(Kind.ROLES, legacy.id(), legacy.name(), "the code " + legacy.code() + " became " + code);
            }
            long id = roles.saveAndFlush(role).requireId();
            ids.put(legacy.id(), id);
            report.mapped(Kind.ROLES, legacy.id(), id);
            report.created(Kind.ROLES);
        }
        return ids;
    }

    private static String roleCode(LegacyData.Role role, Set<String> used)
    {
        String raw = Strings.blankToNull(role.code());
        String code = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("^[^a-z0-9]+", "");
        if (code.length() > 64) {
            code = code.substring(0, 64);
        }
        if (!ROLE_CODE.matcher(code).matches()) {
            code = "legacy-role-" + role.id();
        }
        // Two old codes may become the same one; the later role keeps its old ID in its code.
        if (!used.add(code)) {
            code = cut(code, 43) + "-" + role.id();
            used.add(code);
        }
        return code;
    }

    private Application application(String code)
    {
        return applications.findByCode(code)
                .orElseGet(() -> applications.saveAndFlush(Application.create(code, "AuthX (imported)", "Menus of the pre-rebuild server")));
    }

    /**
     * Menus, from the top down: folders ({@code #}) become menus, other URLs pages, and anything below a page a button.
     * Orphans go to the top; what lies below a button, or in a loop of parents, is left out.
     */
    private Map<Long, Resource> menus(LegacyData data, long applicationId, LegacyReport report)
    {
        Map<Long, LegacyData.Menu> byId = new LinkedHashMap<>();
        data.menus().forEach(menu -> byId.put(menu.id(), menu));
        Map<Long, List<LegacyData.Menu>> children = new HashMap<>();
        Deque<LegacyData.Menu> pending = new ArrayDeque<>();
        for (LegacyData.Menu menu : data.menus()) {
            Long parent = menu.parent();
            if (parent == null || parent == menu.id()) {
                pending.add(menu);
            }
            else if (!byId.containsKey(parent)) {
                report.noted(Kind.MENUS, menu.id(), menu.name(), "its parent " + parent + " does not exist; placed at the top");
                pending.add(menu);
            }
            else {
                children.computeIfAbsent(parent, key -> new ArrayList<>()).add(menu);
            }
        }
        Map<Long, Resource> created = new HashMap<>();
        while (!pending.isEmpty()) {
            LegacyData.Menu menu = pending.removeFirst();
            Long parentId = menu.parent();
            Resource parent = parentId == null ? null : created.get(parentId);
            Resource resource = menu(menu, parent, applicationId, report);
            if (resource == null) {
                continue;
            }
            created.put(menu.id(), resource);
            List<LegacyData.Menu> below = new ArrayList<>(children.getOrDefault(menu.id(), List.of()));
            below.sort(Comparator.comparingInt(LegacyData.Menu::sorted).thenComparingLong(LegacyData.Menu::id));
            pending.addAll(below);
        }
        data.menus().stream().filter(menu -> !created.containsKey(menu.id()) && !skipped(report, menu.id()))
                .forEach(menu -> report.skipped(Kind.MENUS, menu.id(), menu.name(), "its parents form a loop or were left out"));
        return created;
    }

    private static boolean skipped(LegacyReport report, long menuId)
    {
        return report.issues().stream().anyMatch(issue -> issue.kind() == Kind.MENUS && Long.valueOf(menuId).equals(issue.legacyId())
                && LegacyReport.SKIPPED.equals(issue.outcome()));
    }

    private @Nullable Resource menu(LegacyData.Menu menu, @Nullable Resource parent, long applicationId, LegacyReport report)
    {
        String code = "menu-" + menu.id();
        Resource existing = resources.findByApplicationIdAndCode(applicationId, code).orElse(null);
        if (existing != null) {
            report.mapped(Kind.MENUS, menu.id(), existing.requireId());
            report.existing(Kind.MENUS);
            return existing;
        }
        String url = Strings.blankToNull(menu.url());
        boolean folder = url == null || FOLDER.equals(url);
        ResourceType type;
        if (parent == null || parent.getType() == ResourceType.MENU) {
            type = folder ? ResourceType.MENU : ResourceType.PAGE;
        }
        else if (parent.getType() == ResourceType.PAGE) {
            type = ResourceType.ACTION;
        }
        else {
            report.skipped(Kind.MENUS, menu.id(), menu.name(), "it lies below a button, which holds nothing");
            return null;
        }
        // Menus of the old server also listed its APIs, which the console has no page for: kept out of the navigation.
        boolean api = url != null && url.startsWith(API_PREFIX);
        String route = type != ResourceType.ACTION && !api && url != null && ROUTE.matcher(url).matches() && !url.contains("*") ? url : null;
        String name = Strings.blankToNull(menu.name());
        String description = Strings.blankToNull(menu.description());
        ResourceDetails details = new ResourceDetails(cut(name == null ? code : name, Resource.NAME_MAX),
                description == null ? null : cut(description, Resource.DESCRIPTION_MAX), route, !api, menu.active(), DenyMode.HIDE);
        Resource resource;
        try {
            resource = Resource.create(applicationId, parent, type, code, details, menu.sorted());
        }
        catch (IllegalArgumentException refused) {
            report.skipped(Kind.MENUS, menu.id(), menu.name(), String.valueOf(refused.getMessage()));
            return null;
        }
        long id = resources.saveAndFlush(resource).requireId();
        report.mapped(Kind.MENUS, menu.id(), id);
        report.created(Kind.MENUS);
        return resource;
    }

    /**
     * URLs with their HTTP methods become API resources {@code api:<METHOD>:<pattern>}, whose permission applications
     * check is {@code <METHOD>:<pattern>}. The old server matched a {@code *} as a prefix; the pattern ends in
     * {@code /**} instead, which matches by path segments, so every such pattern is listed for a check.
     */
    private Map<Long, List<Resource>> apis(LegacyData data, long applicationId, Map<Long, Resource> menus, LegacyReport report)
    {
        Map<Long, String> methods = new HashMap<>();
        data.methods().forEach(method -> {
            String verb = Strings.blankToNull(method.method());
            if (verb != null) {
                methods.put(method.id(), verb.toUpperCase(Locale.ROOT));
            }
        });
        Map<Long, LegacyData.Menu> byId = new HashMap<>();
        data.menus().forEach(menu -> byId.put(menu.id(), menu));
        Map<String, Resource> byCode = new HashMap<>();
        Map<Long, List<Resource>> found = new HashMap<>();
        Set<Long> reported = new HashSet<>();
        for (LegacyData.Link link : data.menuMethods()) {
            LegacyData.Menu menu = byId.get(link.from());
            String url = menu == null ? null : Strings.blankToNull(menu.url());
            if (menu == null || url == null || FOLDER.equals(url) || !menus.containsKey(menu.id())) {
                continue;
            }
            String verb = methods.get(link.to());
            if (verb == null || !METHODS.contains(verb)) {
                report.noted(Kind.APIS, menu.id(), menu.name(), "the HTTP method " + verb + " is not one; left out");
                continue;
            }
            String pattern = pattern(url);
            if (url.contains("*") && reported.add(menu.id())) {
                report.wildcard(menu.id(), url, pattern);
            }
            String code = "api:" + verb + ":" + pattern;
            if (code.length() > Resource.CODE_MAX) {
                report.skipped(Kind.APIS, menu.id(), menu.name(), "the URL is too long for a resource code");
                continue;
            }
            Resource api = byCode.computeIfAbsent(code, key -> api(applicationId, key, verb + " " + pattern, report));
            found.computeIfAbsent(menu.id(), key -> new ArrayList<>()).add(api);
        }
        return found;
    }

    private Resource api(long applicationId, String code, String name, LegacyReport report)
    {
        Resource existing = resources.findByApplicationIdAndCode(applicationId, code).orElse(null);
        if (existing != null) {
            report.existing(Kind.APIS);
            return existing;
        }
        Resource resource = Resource.create(applicationId, null, ResourceType.API, code,
                new ResourceDetails(cut(name, Resource.NAME_MAX), null, null, true, true, DenyMode.HIDE), 0);
        resources.saveAndFlush(resource);
        report.created(Kind.APIS);
        return resource;
    }

    /** Cuts a text to a column's length. */
    private static String cut(String value, int max)
    {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** An old URL as a path pattern: a leading slash, and a {@code *} as everything below what precedes it. */
    static String pattern(String url)
    {
        String path = url.strip();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        int star = path.indexOf('*');
        if (star < 0) {
            return path;
        }
        String prefix = path.substring(0, star);
        return (prefix.endsWith("/") ? prefix : prefix + "/") + "**";
    }

    /** Each role's menus, and the APIs of those menus, as explicit grants. */
    private void grant(LegacyData data, Map<Long, Long> roleIds, Map<Long, Resource> menus, Map<Long, List<Resource>> apis, LegacyReport report)
    {
        Set<String> granted = grants.findByRoleIdIn(Set.copyOf(roleIds.values())).stream()
                .map(existing -> existing.getRoleId() + ":" + existing.getResourceId()).collect(Collectors.toCollection(HashSet::new));
        for (LegacyData.Link link : data.roleMenus()) {
            Long roleId = roleIds.get(link.from());
            Resource menu = menus.get(link.to());
            if (roleId == null || menu == null) {
                report.skipped(Kind.GRANTS, null, "role " + link.from() + " → menu " + link.to(), "the role or the menu was left out");
                continue;
            }
            List<Resource> targets = new ArrayList<>(List.of(menu));
            targets.addAll(apis.getOrDefault(link.to(), List.of()));
            for (Resource target : targets) {
                if (!granted.add(roleId + ":" + target.requireId())) {
                    report.existing(Kind.GRANTS);
                    continue;
                }
                grants.save(RoleGrant.create(roleId, target, GrantEffect.ALLOW, null, IMPORT));
                report.created(Kind.GRANTS);
            }
        }
        grants.flush();
    }

    /** Each account's roles, as direct assignments without an end. */
    private void assign(LegacyData data, Map<Long, Long> accountIds, Map<Long, Long> roleIds, LegacyReport report)
    {
        for (LegacyData.Link link : data.userRoles()) {
            Long accountId = accountIds.get(link.from());
            Long roleId = roleIds.get(link.to());
            if (accountId == null || roleId == null) {
                report.skipped(Kind.ASSIGNMENTS, null, "user " + link.from() + " → role " + link.to(), "the account or the role was left out");
                continue;
            }
            if (assignments.findByRoleIdAndSubjectTypeAndSubjectId(roleId, SubjectType.USER, accountId).isPresent()) {
                report.existing(Kind.ASSIGNMENTS);
                continue;
            }
            assignments.saveAndFlush(RoleAssignment.create(roleId, SubjectType.USER, accountId, RoleAssignment.Terms.UNLIMITED));
            report.created(Kind.ASSIGNMENTS);
        }
    }
}
