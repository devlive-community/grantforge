// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleParent;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.identity.domain.AccountPosition;
import org.devlive.grantforge.identity.domain.GroupMember;
import org.devlive.grantforge.identity.domain.OrgMember;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.Position;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserGroup;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;

/**
 * Seeds a tenant at scale through the application's own entities, so the data is what the services would write:
 * departments, groups and positions; accounts in them; custom roles, some inheriting from others, assigned to groups,
 * departments, positions and some accounts directly; grants of console pages; and an application with a large
 * resource tree that some roles are granted parts of. Rows are written in batches, one transaction per batch.
 */
final class Seeder
{
    /** Accounts written per transaction. */
    static final int BATCH = 1000;

    /** The account the member benchmarks sign in as; it may read groups. */
    static final String MEMBER = "perf-member";

    private static final int DIVISIONS = 10;
    private static final int DEPARTMENTS_PER_DIVISION = 10;
    private static final int POSITIONS = 100;
    private static final int PAGES_PER_MODULE = 100;
    private static final int ACTIONS_PER_PAGE = 9;
    private static final int CONSOLE_GRANTS_PER_ROLE = 5;
    private static final int APPLICATION_GRANTS_PER_ROLE = 20;

    private final PerfSettings settings;
    private final long tenantId;
    private final EntityManager entities;
    private final TransactionTemplate transactions;
    private final PasswordEncoder passwords;
    private final Instant now;

    /**
     * Prepares seeding of a tenant.
     *
     * @param settings the scale
     * @param context the running server
     * @param tenantId the tenant to fill
     */
    Seeder(PerfSettings settings, ApplicationContext context, long tenantId)
    {
        this.settings = requireNonNull(settings, "settings");
        this.tenantId = tenantId;
        this.entities = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
        this.transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        this.passwords = context.getBean(PasswordEncoder.class);
        this.now = context.getBean(Clock.class).instant();
    }

    /**
     * Seeds everything.
     *
     * @param adminId the administrator, who grants the roles
     * @param memberPassword the password of {@link #MEMBER}
     * @return what was seeded
     */
    Seeded seed(long adminId, String memberPassword)
    {
        List<Long> departments = departments();
        List<Long> groups = in(() -> persistAll(range(Math.max(10, settings.users() / 1000)),
                index -> UserGroup.create(code("perf-group", index), "Perf group " + index, null)));
        List<Long> positions = in(() -> persistAll(range(POSITIONS), index -> Position.create(code("perf-position", index), "Perf position " + index,
                null, index)));
        List<Long> roles = roles();
        assignRoles(roles, groups, departments, positions);
        long[] accounts = accounts(roles, groups, departments, positions);
        grantConsolePages(roles, adminId);
        long applicationId = application(roles, adminId);
        long member = member(roles, groups.get(0), departments.get(0), memberPassword, adminId);
        return new Seeded(accounts, roles, applicationId, member);
    }

    /**
     * Finds an account of the tenant.
     *
     * @param username its user name
     * @return its ID
     * @throws jakarta.persistence.NoResultException if there is none
     */
    long accountId(String username)
    {
        return in(() -> entities.createQuery("select a from UserAccount a where a.username = :username", UserAccount.class)
                .setParameter("username", username).getSingleResult().requireId());
    }

    private List<Long> departments()
    {
        return in(() -> {
            OrgUnit root = persist(OrgUnit.create(null, "perf", "Perf company", 0));
            List<Long> departments = new ArrayList<>();
            for (int division = 0; division < DIVISIONS; division++) {
                OrgUnit parent = persist(OrgUnit.create(root, code("perf-division", division), "Perf division " + division, division));
                for (int department = 0; department < DEPARTMENTS_PER_DIVISION; department++) {
                    int index = division * DEPARTMENTS_PER_DIVISION + department;
                    departments.add(persist(OrgUnit.create(parent, code("perf-dept", index), "Perf department " + index, department)).requireId());
                }
            }
            return departments;
        });
    }

    /** Roles; every tenth role from the hundredth on inherits from one of the first hundred, which inherit from none. */
    private List<Long> roles()
    {
        List<Long> roles = new ArrayList<>();
        for (int from = 0; from < settings.roles(); from += BATCH) {
            int first = from;
            roles.addAll(in(() -> persistAll(range(first, Math.min(first + BATCH, settings.roles())),
                    index -> Role.create(code("perf-role", index), "Perf role " + index, null))));
        }
        run(() -> {
            for (int index = 100; index < roles.size(); index += 10) {
                entities.persist(RoleParent.of(roles.get(index), roles.get(index % 100)));
            }
        });
        return roles;
    }

    /** Each group gets three roles, each department two and each position one. */
    private void assignRoles(List<Long> roles, List<Long> groups, List<Long> departments, List<Long> positions)
    {
        run(() -> {
            for (int index = 0; index < groups.size(); index++) {
                for (int offset = 0; offset < 3; offset++) {
                    assign(roles.get((index * 3 + offset) % roles.size()), SubjectType.GROUP, groups.get(index));
                }
            }
            for (int index = 0; index < departments.size(); index++) {
                for (int offset = 0; offset < 2; offset++) {
                    assign(roles.get((index * 2 + offset + 7) % roles.size()), SubjectType.ORG_UNIT, departments.get(index));
                }
            }
            for (int index = 0; index < positions.size(); index++) {
                assign(roles.get((index + 11) % roles.size()), SubjectType.POSITION, positions.get(index));
            }
        });
    }

    /** Accounts in a department and a group each; every tenth holds a position, every twentieth has a role of its own. */
    private long[] accounts(List<Long> roles, List<Long> groups, List<Long> departments, List<Long> positions)
    {
        long[] accounts = new long[settings.users()];
        // Every account gets the same hash; hashing a million passwords would take hours and sign-ins are not measured.
        String hash = passwords.encode("not used for signing in");
        for (int from = 0; from < accounts.length; from += BATCH) {
            int first = from;
            run(() -> {
                for (int index = first; index < Math.min(first + BATCH, accounts.length); index++) {
                    long account = persist(UserAccount.create(String.format(Locale.ROOT, "perf-user-%07d", index), hash, now)
                            .withDisplayName("Perf User " + index)).requireId();
                    accounts[index] = account;
                    entities.persist(OrgMember.of(account, departments.get(index % departments.size()), true));
                    entities.persist(GroupMember.of(groups.get(index % groups.size()), account));
                    if (index % 10 == 0) {
                        entities.persist(AccountPosition.of(account, positions.get(index / 10 % positions.size())));
                    }
                    if (index % 20 == 0) {
                        assign(roles.get(index / 20 % roles.size()), SubjectType.USER, account);
                    }
                }
            });
        }
        return accounts;
    }

    /** Every role is granted a few pages of the console. */
    private void grantConsolePages(List<Long> roles, long adminId)
    {
        List<Resource> pages = in(() -> entities.createQuery("select r from Resource r where r.applicationId = (select a.id from Application a "
                + "where a.code = :console) and r.type = :page order by r.code", Resource.class)
                .setParameter("console", Application.CONSOLE).setParameter("page", ResourceType.PAGE).getResultList());
        if (pages.isEmpty()) {
            throw new IllegalStateException("the console catalog has no pages");
        }
        for (int from = 0; from < roles.size(); from += BATCH) {
            int first = from;
            run(() -> {
                for (int index = first; index < Math.min(first + BATCH, roles.size()); index++) {
                    for (int offset = 0; offset < Math.min(CONSOLE_GRANTS_PER_ROLE, pages.size()); offset++) {
                        Resource page = pages.get((index * CONSOLE_GRANTS_PER_ROLE + offset) % pages.size());
                        entities.persist(RoleGrant.create(roles.get(index), page, GrantEffect.ALLOW, null, adminId));
                    }
                }
            });
        }
    }

    /**
     * An application of modules with a hundred pages of nine actions each; the first tenth of the roles are granted
     * twenty of its pages and actions each.
     */
    private long application(List<Long> roles, long adminId)
    {
        long applicationId = in(() -> persist(Application.create("perf-app", "Perf application", null)).requireId());
        List<Resource> granted = new ArrayList<>();
        int modules = Math.max(1, settings.resources() / (1 + PAGES_PER_MODULE * (1 + ACTIONS_PER_PAGE)));
        for (int module = 0; module < modules; module++) {
            int current = module;
            run(() -> {
                String moduleCode = code("m", current);
                Resource parent = persist(Resource.create(applicationId, null, ResourceType.MODULE, moduleCode, details("Module " + current),
                        current));
                for (int page = 0; page < PAGES_PER_MODULE; page++) {
                    Resource screen = persist(Resource.create(applicationId, parent, ResourceType.PAGE, moduleCode + "." + code("p", page),
                            details("Page " + page), page));
                    if (page % 25 == 0) {
                        granted.add(screen);
                    }
                    for (int action = 0; action < ACTIONS_PER_PAGE; action++) {
                        Resource button = persist(Resource.create(applicationId, screen, ResourceType.ACTION, screen.getCode() + ".a" + action,
                                details("Action " + action), action));
                        if (action == page % ACTIONS_PER_PAGE) {
                            granted.add(button);
                        }
                    }
                }
            });
        }
        int grantees = Math.max(1, roles.size() / 10);
        for (int from = 0; from < grantees; from += BATCH) {
            int first = from;
            run(() -> {
                for (int index = first; index < Math.min(first + BATCH, grantees); index++) {
                    for (int offset = 0; offset < APPLICATION_GRANTS_PER_ROLE; offset++) {
                        Resource resource = granted.get((index * 37 + offset * 101) % granted.size());
                        entities.persist(RoleGrant.create(roles.get(index), resource, GrantEffect.ALLOW, null, adminId));
                    }
                }
            });
        }
        return applicationId;
    }

    /** The member signs in for the API benchmarks; besides the usual memberships its role lets it read groups. */
    private long member(List<Long> roles, long group, long department, String password, long adminId)
    {
        return in(() -> {
            long member = persist(UserAccount.create(MEMBER, passwords.encode(password), now).withDisplayName("Perf Member")).requireId();
            entities.persist(OrgMember.of(member, department, true));
            entities.persist(GroupMember.of(group, member));
            Role reader = persist(Role.create("perf-group-reader", "Perf group reader", null));
            Resource groupsPage = entities.createQuery("select r from Resource r where r.code = :code", Resource.class)
                    .setParameter("code", "system.group").getSingleResult();
            entities.persist(RoleGrant.create(reader.requireId(), groupsPage, GrantEffect.ALLOW, null, adminId));
            assign(reader.requireId(), SubjectType.USER, member);
            assign(roles.get(1), SubjectType.USER, member);
            return member;
        });
    }

    private void assign(long roleId, SubjectType type, long subjectId)
    {
        entities.persist(RoleAssignment.create(roleId, type, subjectId, RoleAssignment.Terms.UNLIMITED));
    }

    private <T> T persist(T entity)
    {
        entities.persist(entity);
        return entity;
    }

    private <T extends BaseEntity> List<Long> persistAll(int[] indexes, IntFunction<T> create)
    {
        List<Long> ids = new ArrayList<>(indexes.length);
        for (int index : indexes) {
            ids.add(persist(create.apply(index)).requireId());
        }
        return ids;
    }

    /** Runs work in the tenant, in one transaction, and frees the persistence context afterwards. */
    private <T> T in(Supplier<T> work)
    {
        return TenantContext.callInTenant(tenantId, () -> requireNonNull(transactions.execute(status -> {
            T result = work.get();
            entities.flush();
            entities.clear();
            return result;
        })));
    }

    /** Runs work without a result in the tenant, in one transaction, and frees the persistence context afterwards. */
    private void run(Runnable work)
    {
        TenantContext.runInTenant(tenantId, () -> transactions.executeWithoutResult(status -> {
            work.run();
            entities.flush();
            entities.clear();
        }));
    }

    private static ResourceDetails details(String name)
    {
        return new ResourceDetails(name, null, null, true, true, DenyMode.HIDE);
    }

    private static String code(String prefix, int index)
    {
        return String.format(Locale.ROOT, "%s-%05d", prefix, index);
    }

    private static int[] range(int count)
    {
        return range(0, count);
    }

    private static int[] range(int from, int to)
    {
        return IntStream.range(from, to).toArray();
    }

    /**
     * What was seeded.
     *
     * @param accounts the seeded accounts, by index
     * @param roles the seeded roles, by index
     * @param applicationId the application with the large resource tree
     * @param memberId the member's account
     */
    record Seeded(long[] accounts, List<Long> roles, long applicationId, long memberId)
    {
        /** Copies the values. */
        Seeded
        {
            accounts = accounts.clone();
            roles = List.copyOf(roles);
        }

        /**
         * Returns the seeded accounts.
         *
         * @return a copy of the IDs
         */
        @Override
        public long[] accounts()
        {
            return accounts.clone();
        }
    }
}
