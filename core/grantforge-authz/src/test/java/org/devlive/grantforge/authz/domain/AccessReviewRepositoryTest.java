// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Access reviews, their roles, rounds and items, of tenants. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccessReviewRepositoryTest
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleAssignmentRepository assignments;

    @Autowired
    private AccessReviewRepository reviews;

    @Autowired
    private AccessReviewRoleRepository reviewRoles;

    @Autowired
    private AccessReviewRoundRepository rounds;

    @Autowired
    private AccessReviewItemRepository items;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            items.deleteAllInBatch();
            rounds.deleteAllInBatch();
            reviewRoles.deleteAllInBatch();
            reviews.deleteAllInBatch();
            assignments.deleteAllInBatch();
            roles.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private AccessReview review(String name, boolean enabled, Instant next)
    {
        AccessReview review = AccessReview.create();
        review.configure(name, null, 7, 30, ReviewFallback.KEEP, enabled, next);
        return reviews.save(review);
    }

    @Test
    void findsReviewsRoundsAndItems()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long reports = TenantContext.callInTenant(acme, () -> roles.save(Role.create("reports", "Reports", null)).requireId());
        RoleAssignment toAlice = TenantContext.callInTenant(acme, () -> assignments.save(RoleAssignment.create(reports, SubjectType.USER, 7,
                RoleAssignment.Terms.UNLIMITED)));
        RoleAssignment toDev = TenantContext.callInTenant(acme, () -> assignments.save(RoleAssignment.create(reports, SubjectType.GROUP, 8,
                RoleAssignment.Terms.UNLIMITED)));
        AccessReview quarterly = TenantContext.callInTenant(acme, () -> review("Quarterly", true, NOW));
        TenantContext.callInTenant(acme, () -> review("Annual", false, NOW));
        long id = quarterly.requireId();
        TenantContext.callInTenant(acme, () -> reviewRoles.save(AccessReviewRole.of(id, reports)));
        AccessReviewRound round = TenantContext.callInTenant(acme, () -> rounds.save(AccessReviewRound.open(id, null, NOW, NOW.plusSeconds(60))));
        long roundId = round.requireId();
        AccessReviewItem kept = AccessReviewItem.of(roundId, toAlice);
        kept.decide(ReviewDecision.KEEP, 9, NOW, null);
        TenantContext.callInTenant(acme, () -> items.saveAll(List.of(kept, AccessReviewItem.of(roundId, toDev))));

        assertThat(TenantContext.callInTenant(acme, () -> reviews.findAllByOrderByNameAscIdAsc())).extracting(AccessReview::getName)
                .containsExactly("Annual", "Quarterly");
        assertThat(TenantContext.callAsSystem(() -> reviews.findByEnabledTrueAndNextRunAtLessThanEqual(NOW))).extracting(AccessReview::getName)
                .containsExactly("Quarterly");
        assertThat(TenantContext.callAsSystem(() -> reviews.findByEnabledTrueAndNextRunAtLessThanEqual(NOW.minusSeconds(1)))).isEmpty();
        assertThat(TenantContext.callInTenant(acme, () -> reviewRoles.findByReviewIdIn(List.of(id)))).extracting(AccessReviewRole::getRoleId)
                .containsExactly(reports);
        assertThat(TenantContext.callInTenant(acme, () -> rounds.findByReviewIdOrderByStartedAtDescIdDesc(id, Limit.of(5)))).hasSize(1);
        assertThat(TenantContext.callInTenant(acme, () -> rounds.findByReviewIdInAndStatus(List.of(id), ReviewRoundStatus.OPEN))).hasSize(1);
        assertThat(TenantContext.callInTenant(acme, () -> rounds.existsByReviewIdAndStatus(id, ReviewRoundStatus.COMPLETED))).isFalse();
        assertThat(TenantContext.callAsSystem(() -> rounds.findByStatusAndDueAtLessThanEqual(ReviewRoundStatus.OPEN, NOW.plusSeconds(60)))).hasSize(1);
        assertThat(TenantContext.callAsSystem(() -> rounds.findByStatusAndDueAtLessThanEqual(ReviewRoundStatus.OPEN, NOW))).isEmpty();

        PageRequest firstPage = PageRequest.of(0, 1, Sort.by("id"));
        assertThat(TenantContext.callInTenant(acme, () -> items.findByRoundId(roundId, firstPage)).getTotalElements()).isEqualTo(2);
        assertThat(TenantContext.callInTenant(acme, () -> items.findByRoundIdAndDecisionIn(roundId, Set.of(ReviewDecision.PENDING), firstPage))
                .getContent()).extracting(AccessReviewItem::getSubjectType).containsExactly(SubjectType.GROUP);
        assertThat(TenantContext.callInTenant(acme, () -> items.findByRoundIdOrderByIdAsc(roundId))).hasSize(2);
        assertThat(TenantContext.callInTenant(acme, () -> items.tally(List.of(roundId)))).containsExactlyInAnyOrder(
                new ReviewTally(roundId, ReviewDecision.KEEP, null, 1), new ReviewTally(roundId, ReviewDecision.PENDING, null, 1));

        // A role is reviewed once per review.
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> reviewRoles.saveAndFlush(AccessReviewRole.of(id, reports))))
                .isInstanceOf(DataIntegrityViolationException.class);

        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        TenantContext.runInTenant(acme, () -> transactions.executeWithoutResult(status -> {
            assertThat(items.deleteByReview(id)).isEqualTo(2);
            assertThat(rounds.deleteByReview(id)).isOne();
            assertThat(reviewRoles.deleteByReview(id)).isOne();
        }));
        assertThat(TenantContext.callAsSystem(() -> items.count())).isZero();
    }

    @Test
    void lockingARoundRaisesItsVersion()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long id = TenantContext.callInTenant(acme, () -> review("Quarterly", true, NOW)).requireId();
        long roundId = TenantContext.callInTenant(acme, () -> rounds.save(AccessReviewRound.open(id, null, NOW, NOW.plusSeconds(60)))).requireId();
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        AccessReviewRound stale = TenantContext.callInTenant(acme, () -> rounds.findById(roundId)).orElseThrow();

        TenantContext.runInTenant(acme, () -> transactions.executeWithoutResult(status -> assertThat(rounds.findForUpdate(roundId)).isPresent()));

        assertThat(TenantContext.callInTenant(acme, () -> rounds.findById(roundId)).orElseThrow().getVersion()).isGreaterThan(stale.getVersion());
        stale.end(ReviewRoundStatus.CANCELLED, null, NOW);
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> rounds.saveAndFlush(stale)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
