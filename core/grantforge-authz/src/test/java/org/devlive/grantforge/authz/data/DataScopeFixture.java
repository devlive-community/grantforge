// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.identity.domain.OrgMember;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Data for data scope tests, the same on every database: in tenant acme the departments hq, rd below hq and ops; alice in
 * rd, bob in ops, carol in hq and dave in none (disabled, without e-mail); audit events by alice and bob, and in tenant
 * globex one by eve.
 */
public final class DataScopeFixture
{
    /** A fixed moment the data is relative to. */
    public static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private final TenantRepository tenants;
    private final UserAccountRepository accounts;
    private final OrgUnitRepository units;
    private final OrgMemberRepository members;
    private final AuditEventRepository events;
    private final EntityManagerFactory factory;
    private final TransactionTemplate transactions;

    public final long acme;
    public final long globex;
    public final OrgUnit hq;
    public final OrgUnit rd;
    public final OrgUnit ops;
    public final long alice;
    public final long bob;
    public final long carol;
    public final long dave;
    public final long eve;

    public DataScopeFixture(TenantRepository tenants, UserAccountRepository accounts, OrgUnitRepository units, OrgMemberRepository members,
            AuditEventRepository events, EntityManagerFactory factory, PlatformTransactionManager transactionManager)
    {
        this.tenants = tenants;
        this.accounts = accounts;
        this.units = units;
        this.members = members;
        this.events = events;
        this.factory = factory;
        this.transactions = new TransactionTemplate(transactionManager);
        acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        globex = tenants.save(Tenant.create("globex", "Globex")).requireId();
        hq = inAcme(() -> units.save(OrgUnit.create(null, "hq", "HQ", 0)));
        rd = inAcme(() -> units.save(OrgUnit.create(hq, "rd", "R&D", 0)));
        ops = inAcme(() -> units.save(OrgUnit.create(null, "ops", "Ops_50%", 1)));
        alice = account("alice", "alice@acme.test", NOW.minusSeconds(86_400), rd);
        bob = account("bob", "bob_ops@acme.test", NOW.minusSeconds(864_000), ops);
        carol = account("carol", "carol@other.test", null, hq);
        dave = inAcme(() -> {
            UserAccount account = UserAccount.create("dave", "h", NOW);
            account.disable();
            return accounts.save(account).requireId();
        });
        eve = TenantContext.callInTenant(globex, () -> accounts.save(UserAccount.create("eve", "h", NOW)).requireId());
        events.save(AuditEvent.of(NOW, AuditAction.LOGOUT, AuditOutcome.SUCCESS, acme, alice, "alice", null, null, null, null, null));
        events.save(AuditEvent.of(NOW, AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE, acme, bob, "bob", null, null, null, null, null));
        events.save(AuditEvent.of(NOW, AuditAction.LOGOUT, AuditOutcome.SUCCESS, globex, eve, "eve", null, null, null, null, null));
    }

    private long account(String username, String email, @Nullable Instant lastLogin, OrgUnit unit)
    {
        return inAcme(() -> {
            UserAccount account = UserAccount.create(username, "h", NOW).withEmail(email);
            if (lastLogin != null) {
                account.recordSuccessfulLogin(lastLogin);
            }
            long id = accounts.save(account).requireId();
            members.save(OrgMember.of(id, unit.requireId(), true));
            return id;
        });
    }

    public <T> T inAcme(Supplier<T> action)
    {
        return TenantContext.callInTenant(acme, action::get);
    }

    /**
     * Runs a specification in tenant acme and maps the rows found.
     *
     * @param type the entity class
     * @param specification the specification
     * @param name what to report of each row
     * @param <T> the entity class
     * @return what was reported, sorted
     */
    public <T> List<String> find(Class<T> type, Specification<T> specification, Function<T, String> name)
    {
        return inAcme(() -> requireNonNull(transactions.execute(status -> {
            EntityManager manager = factory.createEntityManager();
            try {
                CriteriaBuilder builder = manager.getCriteriaBuilder();
                CriteriaQuery<T> query = builder.createQuery(type);
                Root<T> root = query.from(type);
                query.select(root).where(specification.toPredicate(root, query, builder));
                return manager.createQuery(query).getResultList().stream().map(name).sorted().toList();
            }
            finally {
                manager.close();
            }
        })));
    }

    /** Deletes what the fixture created. */
    public void delete()
    {
        TenantContext.callAsSystem(() -> {
            members.deleteAllInBatch();
            accounts.deleteAllInBatch();
            units.deleteAllInBatch();
            return null;
        });
        events.deleteAllInBatch();
        tenants.deleteAllInBatch();
    }
}
