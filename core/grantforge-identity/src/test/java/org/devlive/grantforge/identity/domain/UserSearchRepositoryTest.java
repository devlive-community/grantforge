// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The account search, through {@link UserAccountRepository}. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserSearchRepositoryTest
{
    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");
    private static final Specification<UserAccount> EVERYONE = Specification.unrestricted();

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private OrgMemberRepository members;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long tenant;
    private OrgUnit hq;
    private OrgUnit sales;

    @BeforeEach
    void createData()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        hq = inTenant(() -> units.save(OrgUnit.create(null, "hq", "总部", 0)));
        sales = inTenant(() -> units.save(OrgUnit.create(hq, "sales", "销售部", 0)));
        long alice = save(UserAccount.create("alice", "h", NOW).withDisplayName("Alice Liddell").withEmail("alice@acme.io"));
        long bob = save(UserAccount.create("bob", "h", NOW).withDisplayName("鲍勃"));
        UserAccount carol = UserAccount.create("carol", "h", NOW);
        carol.disable();
        save(carol);
        UserAccount dave = UserAccount.create("dave", "h", NOW);
        dave.lockIndefinitely();
        save(dave);
        UserAccount erin = UserAccount.create("erin", "h", NOW);
        // A lock that already ended counts as active again.
        erin.recordFailedLogin(NOW.minus(Duration.ofHours(1)), 1, Duration.ofMinutes(15));
        save(erin);
        inTenant(() -> {
            members.save(OrgMember.of(alice, hq.requireId(), true));
            members.save(OrgMember.of(alice, sales.requireId(), false));
            return members.save(OrgMember.of(bob, sales.requireId(), true));
        });
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            members.deleteAllInBatch();
            accounts.deleteAllInBatch();
            return null;
        });
        inTenant(() -> {
            units.delete(units.findById(sales.requireId()).orElseThrow());
            units.delete(units.findById(hq.requireId()).orElseThrow());
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private <T> T inTenant(Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, () -> new TransactionTemplate(transactionManager).execute(status ->
                action.get()));
    }

    private long save(UserAccount account)
    {
        return inTenant(() -> accounts.save(account)).requireId();
    }

    private List<String> names(UserCriteria criteria)
    {
        return inTenant(() -> accounts.search(criteria, EVERYONE, NOW, 0, 50)).stream().map(UserRow::username).sorted().toList();
    }

    @Test
    void pagesNewestFirstWithTheTotal()
    {
        List<UserRow> all = inTenant(() -> accounts.search(UserCriteria.ALL, EVERYONE, NOW, 0, 50));
        UserPage first = inTenant(() -> accounts.page(UserCriteria.ALL, EVERYONE, NOW, 0, 2));

        assertThat(first.total()).isEqualTo(5);
        assertThat(first.rows()).containsExactlyElementsOf(all.subList(0, 2));
        assertThat(all).isSortedAccordingTo(Comparator.comparing(UserRow::createdAt).thenComparing(UserRow::id).reversed());
        UserPage last = inTenant(() -> accounts.page(UserCriteria.ALL, EVERYONE, NOW, 4, 2));
        assertThat(last.rows()).containsExactly(all.get(4));
        assertThat(last.total()).isEqualTo(5);
        UserPage beyond = inTenant(() -> accounts.page(UserCriteria.ALL, EVERYONE, NOW, 10, 2));
        assertThat(beyond.rows()).isEmpty();
        assertThat(beyond.total()).isEqualTo(5);
        assertThat(inTenant(() -> accounts.page(new UserCriteria("ali", null, null, null), EVERYONE, NOW, 0, 2)).total()).isOne();
    }

    @Test
    void countsWhenThereAreManyMatches()
    {
        inTenant(() -> accounts.saveAll(IntStream.range(0, UserSearchRepositoryImpl.SMALL)
                .mapToObj(index -> UserAccount.create(String.format("bulk-%04d", index), "h", NOW)).toList()));
        List<UserRow> newest = inTenant(() -> accounts.search(UserCriteria.ALL, EVERYONE, NOW, 0, 3));

        UserPage page = inTenant(() -> accounts.page(UserCriteria.ALL, EVERYONE, NOW, 0, 3));
        assertThat(page.total()).isEqualTo(UserSearchRepositoryImpl.SMALL + 5L);
        assertThat(page.rows()).containsExactlyElementsOf(newest).hasSize(3);
        assertThat(inTenant(() -> accounts.page(new UserCriteria("bulk-000", null, null, null), EVERYONE, NOW, 0, 20)).total())
                .isEqualTo(10);
    }

    @Test
    void listsEveryAccountWithItsPrimaryDepartment()
    {
        List<UserRow> all = inTenant(() -> accounts.search(UserCriteria.ALL, EVERYONE, NOW, 0, 50));

        assertThat(all).hasSize(5);
        assertThat(all).filteredOn(row -> row.username().equals("alice")).singleElement()
                .extracting(UserRow::primaryUnitName, UserRow::email).containsExactly("总部", "alice@acme.io");
        assertThat(all).filteredOn(row -> row.username().equals("carol")).singleElement()
                .extracting(UserRow::primaryUnitId).isNull();
        assertThat(inTenant(() -> accounts.count(UserCriteria.ALL, EVERYONE, NOW))).isEqualTo(5);
        assertThat(inTenant(() -> accounts.search(UserCriteria.ALL, EVERYONE, NOW, 4, 50))).hasSize(1);
        assertThat(TenantContext.callInTenant(tenant + 1, () -> accounts.count(UserCriteria.ALL, EVERYONE, NOW))).isZero();
    }

    @Test
    void filtersByTextStateAndDepartment()
    {
        assertThat(names(new UserCriteria("lid", null, null, null))).containsExactly("alice");
        assertThat(names(new UserCriteria("鲍", null, null, null))).containsExactly("bob");
        assertThat(names(new UserCriteria("acme.io", null, null, null))).containsExactly("alice");
        assertThat(names(new UserCriteria(null, UserState.ACTIVE, null, null))).containsExactly("alice", "bob", "erin");
        assertThat(names(new UserCriteria(null, UserState.DISABLED, null, null))).containsExactly("carol");
        assertThat(names(new UserCriteria(null, UserState.LOCKED, null, null))).containsExactly("dave");
        assertThat(names(new UserCriteria(null, null, null, hq.requireId()))).containsExactly("alice");
        assertThat(names(new UserCriteria(null, null, hq.getPath(), null))).containsExactly("alice", "bob");
        assertThat(names(new UserCriteria(null, null, sales.getPath(), null))).containsExactly("alice", "bob");
        assertThat(inTenant(() -> accounts.count(new UserCriteria("b", UserState.ACTIVE, hq.getPath(), null), EVERYONE, NOW)))
                .isOne();
    }

    @Test
    void keepsToTheReadersScope()
    {
        Specification<UserAccount> notBob = (root, query, builder) -> builder.notEqual(root.get("usernameNorm"), "bob");
        Specification<UserAccount> nobody = (root, query, builder) -> builder.disjunction();
        UserCriteria inHq = new UserCriteria(null, null, hq.getPath(), null);

        assertThat(inTenant(() -> accounts.search(inHq, notBob, NOW, 0, 50))).extracting(UserRow::username).containsExactly("alice");
        assertThat(inTenant(() -> accounts.count(inHq, notBob, NOW))).isOne();
        assertThat(inTenant(() -> accounts.search(UserCriteria.ALL, nobody, NOW, 0, 50))).isEmpty();
        assertThat(inTenant(() -> accounts.count(UserCriteria.ALL, nobody, NOW))).isZero();
    }

    @Test
    void leavesEMailAddressesOutOfTheSearchWhenAsked()
    {
        assertThat(names(new UserCriteria("acme.io", null, null, null, false))).isEmpty();
        assertThat(names(new UserCriteria("lid", null, null, null, false))).containsExactly("alice");
        assertThat(new UserCriteria("x", null, null, null).emailSearched()).isTrue();
    }
}
