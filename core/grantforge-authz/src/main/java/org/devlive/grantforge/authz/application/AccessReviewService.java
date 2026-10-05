// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.domain.AccessReview;
import org.devlive.grantforge.authz.domain.AccessReviewItem;
import org.devlive.grantforge.authz.domain.AccessReviewItemRepository;
import org.devlive.grantforge.authz.domain.AccessReviewRepository;
import org.devlive.grantforge.authz.domain.AccessReviewRole;
import org.devlive.grantforge.authz.domain.AccessReviewRoleRepository;
import org.devlive.grantforge.authz.domain.AccessReviewRound;
import org.devlive.grantforge.authz.domain.AccessReviewRoundRepository;
import org.devlive.grantforge.authz.domain.ReviewDecision;
import org.devlive.grantforge.authz.domain.ReviewFallback;
import org.devlive.grantforge.authz.domain.ReviewOutcome;
import org.devlive.grantforge.authz.domain.ReviewRoundStatus;
import org.devlive.grantforge.authz.domain.ReviewTally;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.query.InClauseBatcher;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Access reviews (D-75): reviewers confirm, round after round, that the subjects holding some roles should still hold
 * them. A round puts every assignment of the review's roles under review as it stands when the round starts; reviewers
 * decide to keep or revoke each until the round completes, when revoked assignments are removed and undecided ones
 * follow the review's fallback. Rounds start by hand or on schedule and complete by hand or when due; a review has at
 * most one open round. Nobody decides about an assignment that gives themselves the role. Every method but
 * {@link #runDue} must be called with the tenant bound.
 */
@Service
public final class AccessReviewService
{
    /** The most roles a review may name. */
    static final int MAX_ROLES = 50;

    /** The most items one decision may cover. */
    static final int MAX_DECIDED = 200;

    /** The most rounds a history shows. */
    static final int HISTORY_LIMIT = 50;

    private static final Logger LOG = LoggerFactory.getLogger(AccessReviewService.class);
    private static final Sort BY_ID = Sort.by("id");

    private final AccessReviewRepository reviews;
    private final AccessReviewRoleRepository reviewRoles;
    private final AccessReviewRoundRepository rounds;
    private final AccessReviewItemRepository items;
    private final RoleRepository roles;
    private final RoleAssignmentRepository assignments;
    private final UserAccountRepository accounts;
    private final EffectiveRoles effectiveRoles;
    private final RoleHolders holders;
    private final SubjectDirectory subjects;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param reviews the reviews
     * @param reviewRoles their roles
     * @param rounds their rounds
     * @param items the assignments under review
     * @param roles the roles
     * @param assignments the assignments, to review and remove
     * @param accounts accounts, to leave system accounts' system roles alone
     * @param effectiveRoles tells whether a reviewer holds the platform administrator role
     * @param holders finds whom an assignment to a group, department or position reaches
     * @param subjects names subjects
     * @param audit records every step
     * @param transactionManager opens transactions
     * @param clock the current time
     */
    public AccessReviewService(AccessReviewRepository reviews, AccessReviewRoleRepository reviewRoles, AccessReviewRoundRepository rounds,
            AccessReviewItemRepository items, RoleRepository roles, RoleAssignmentRepository assignments, UserAccountRepository accounts,
            EffectiveRoles effectiveRoles, RoleHolders holders, SubjectDirectory subjects, AuditLog audit,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.reviews = requireNonNull(reviews, "reviews");
        this.reviewRoles = requireNonNull(reviewRoles, "reviewRoles");
        this.rounds = requireNonNull(rounds, "rounds");
        this.items = requireNonNull(items, "items");
        this.roles = requireNonNull(roles, "roles");
        this.assignments = requireNonNull(assignments, "assignments");
        this.accounts = requireNonNull(accounts, "accounts");
        this.effectiveRoles = requireNonNull(effectiveRoles, "effectiveRoles");
        this.holders = requireNonNull(holders, "holders");
        this.subjects = requireNonNull(subjects, "subjects");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Lists the reviews by name, each with its open round.
     *
     * @return the reviews
     */
    public List<AccessReviewView> list()
    {
        return requireNonNull(transactions.execute(status -> views(reviews.findAllByOrderByNameAscIdAsc())));
    }

    /**
     * Adds a review.
     *
     * @param actorId the administrator
     * @param command the review
     * @return the review
     * @throws GrantForgeException with {@link AuthzErrorCode#ACCESS_REVIEW_INVALID} or {@link CommonErrorCode#NOT_FOUND} for an
     *         unknown role
     */
    public AccessReviewView create(long actorId, AccessReviewCommand command)
    {
        return requireNonNull(transactions.execute(status -> {
            AccessReview review = AccessReview.create();
            configure(review, command);
            long id = reviews.saveAndFlush(review).requireId();
            store(id, command.roleIds());
            AccessReviewView view = views(List.of(review)).get(0);
            record(AuditAction.ACCESS_REVIEW_CREATED, actorId, id, roleCodes(view));
            return view;
        }));
    }

    /**
     * Changes a review. An open round keeps the assignments it started with; the changes apply from the next round,
     * except the fallback, which applies when the open round completes.
     *
     * @param actorId the administrator
     * @param id the review
     * @param command the new values
     * @return the review
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or {@link AuthzErrorCode#ACCESS_REVIEW_INVALID}
     */
    public AccessReviewView update(long actorId, long id, AccessReviewCommand command)
    {
        return requireNonNull(transactions.execute(status -> {
            AccessReview review = requireReview(id);
            configure(review, command);
            reviewRoles.deleteByReview(id);
            store(id, command.roleIds());
            reviews.saveAndFlush(review);
            AccessReviewView view = views(List.of(review)).get(0);
            record(AuditAction.ACCESS_REVIEW_UPDATED, actorId, id, roleCodes(view));
            return view;
        }));
    }

    /**
     * Deletes a review with its rounds; an open round ends without applying anything.
     *
     * @param actorId the administrator
     * @param id the review
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public void delete(long actorId, long id)
    {
        transactions.executeWithoutResult(status -> {
            AccessReview review = requireReview(id);
            items.deleteByReview(id);
            rounds.deleteByReview(id);
            reviewRoles.deleteByReview(id);
            reviews.delete(review);
            reviews.flush();
            record(AuditAction.ACCESS_REVIEW_DELETED, actorId, id, review.getName());
        });
    }

    /**
     * Starts a round now, whatever the schedule.
     *
     * @param actorId the administrator
     * @param id the review
     * @return the round
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or {@link AuthzErrorCode#ACCESS_REVIEW_RUNNING}
     */
    public ReviewRoundView start(long actorId, long id)
    {
        return requireNonNull(transactions.execute(status -> roundView(open(requireReview(id), actorId, false))));
    }

    /**
     * Lists a review's rounds, newest first.
     *
     * @param id the review
     * @return the latest rounds
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public List<ReviewRoundView> rounds(long id)
    {
        return requireNonNull(transactions.execute(status -> {
            requireReview(id);
            return roundViews(rounds.findByReviewIdOrderByStartedAtDescIdDesc(id, Limit.of(HISTORY_LIMIT)));
        }));
    }

    /**
     * Lists a page of a round's assignments, in the order they were put under review.
     *
     * @param roundId the round
     * @param decisions the decisions to list; all if empty
     * @param page the page
     * @return the items
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public PageResult<ReviewItemView> items(long roundId, Collection<ReviewDecision> decisions, PageQuery page)
    {
        return requireNonNull(transactions.execute(status -> {
            requireRound(roundId);
            PageRequest request = PageRequest.of(page.page() - 1, page.size(), BY_ID);
            Page<AccessReviewItem> found = decisions.isEmpty() ? items.findByRoundId(roundId, request)
                    : items.findByRoundIdAndDecisionIn(roundId, Set.copyOf(decisions), request);
            return new PageResult<>(itemViews(found.getContent()), page.page(), page.size(), found.getTotalElements());
        }));
    }

    /**
     * Records a reviewer's decision about some assignments of an open round; {@link ReviewDecision#PENDING} takes
     * decisions back. Decisions apply when the round completes, so they can change until then.
     *
     * @param reviewerId the reviewer, who must not get the role through any of the assignments
     * @param roundId the round
     * @param itemIds the assignments under review, 1 to 200
     * @param decision the decision
     * @param comment why, or {@code null}
     * @return the items as decided
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}, {@link AuthzErrorCode#ACCESS_REVIEW_INVALID},
     *         {@link AuthzErrorCode#ACCESS_REVIEW_CLOSED}, {@link AuthzErrorCode#ACCESS_REVIEW_SELF} or
     *         {@link AuthzErrorCode#ROLE_NOT_ASSIGNABLE} for revoking the platform administrator role without holding it
     */
    public List<ReviewItemView> decide(long reviewerId, long roundId, Collection<Long> itemIds, ReviewDecision decision, @Nullable String comment)
    {
        requireNonNull(decision, "decision");
        String said = Strings.blankToNull(comment);
        if (said != null && said.length() > AccessReviewItem.COMMENT_MAX) {
            throw invalid("a comment is at most " + AccessReviewItem.COMMENT_MAX + " characters");
        }
        Set<Long> wanted = new LinkedHashSet<>(itemIds);
        if (wanted.isEmpty() || wanted.size() > MAX_DECIDED) {
            throw invalid("decide about 1-" + MAX_DECIDED + " assignments at a time");
        }
        return requireNonNull(transactions.execute(status -> {
            // Locked so that a decision and the round's completion cannot both go through.
            AccessReviewRound round = rounds.findForUpdate(roundId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no round " + roundId));
            requireOpen(round);
            List<AccessReviewItem> chosen = items.findAllById(wanted).stream().filter(item -> item.getRoundId() == roundId)
                    .sorted(Comparator.comparingLong(AccessReviewItem::requireId)).toList();
            if (chosen.size() != wanted.size()) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "not every item belongs to round " + roundId);
            }
            requireNotOwn(reviewerId, chosen);
            if (decision == ReviewDecision.REVOKE) {
                requireRemovable(reviewerId, chosen);
            }
            Instant now = clock.instant();
            chosen.forEach(item -> item.decide(decision, reviewerId, now, said));
            items.saveAllAndFlush(chosen);
            record(AuditAction.ACCESS_REVIEW_DECIDED, reviewerId, roundId, decision + " " + chosen.size());
            return itemViews(chosen);
        }));
    }

    /**
     * Completes an open round before it is due: revoked assignments are removed, undecided ones follow the review's
     * fallback.
     *
     * @param actorId the administrator
     * @param roundId the round
     * @return the round
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or {@link AuthzErrorCode#ACCESS_REVIEW_CLOSED}
     */
    public ReviewRoundView complete(long actorId, long roundId)
    {
        return requireNonNull(transactions.execute(status -> roundView(completeRound(actorId, roundId))));
    }

    /**
     * Ends an open round without applying its decisions.
     *
     * @param actorId the administrator
     * @param roundId the round
     * @return the round
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or {@link AuthzErrorCode#ACCESS_REVIEW_CLOSED}
     */
    public ReviewRoundView cancel(long actorId, long roundId)
    {
        return requireNonNull(transactions.execute(status -> {
            AccessReviewRound round = rounds.findForUpdate(roundId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no round " + roundId));
            requireOpen(round);
            round.end(ReviewRoundStatus.CANCELLED, actorId, clock.instant());
            rounds.saveAndFlush(round);
            record(AuditAction.ACCESS_REVIEW_CANCELLED, actorId, roundId, Long.toString(round.getReviewId()));
            return roundView(round);
        }));
    }

    /**
     * Completes every tenant's rounds that are due, then starts the rounds whose time has come. A review whose round is
     * still open waits for it to end. A failure is logged and the job goes on, so one round cannot hold up the others;
     * it is tried again next time.
     *
     * @return how many rounds were completed and started
     */
    public int runDue()
    {
        Instant now = clock.instant();
        int done = 0;
        for (AccessReviewRound due : TenantContext.callAsSystem(() -> rounds.findByStatusAndDueAtLessThanEqual(ReviewRoundStatus.OPEN, now))) {
            long roundId = due.requireId();
            done += inTenant(requireNonNull(due.getTenantId(), "tenantId"), "complete round " + roundId, () -> {
                AccessReviewRound round = requireRound(roundId);
                if (round.getStatus() == ReviewRoundStatus.OPEN) {
                    completeRound(null, roundId);
                    return true;
                }
                return false;
            });
        }
        for (AccessReview scheduled : TenantContext.callAsSystem(() -> reviews.findByEnabledTrueAndNextRunAtLessThanEqual(now))) {
            long reviewId = scheduled.requireId();
            done += inTenant(requireNonNull(scheduled.getTenantId(), "tenantId"), "start review " + reviewId, () -> {
                AccessReview review = requireReview(reviewId);
                if (review.isDue(now) && !rounds.existsByReviewIdAndStatus(reviewId, ReviewRoundStatus.OPEN)) {
                    open(review, null, true);
                    return true;
                }
                return false;
            });
        }
        return done;
    }

    private int inTenant(long tenantId, String what, Supplier<Boolean> work)
    {
        try {
            Boolean ran = TenantContext.callInTenant(tenantId, () -> transactions.execute(status -> work.get()));
            return Boolean.TRUE.equals(ran) ? 1 : 0;
        }
        catch (RuntimeException failure) {
            // Another node may have done it first (optimistic lock), or the data is broken; either way try again later.
            LOG.warn("Could not {} of tenant {}: {}", what, tenantId, failure.toString());
            return 0;
        }
    }

    /** Opens a round of a review with every assignment of its roles, except the system roles of system accounts. */
    private AccessReviewRound open(AccessReview review, @Nullable Long actorId, boolean scheduled)
    {
        long reviewId = review.requireId();
        if (rounds.existsByReviewIdAndStatus(reviewId, ReviewRoundStatus.OPEN)) {
            throw new GrantForgeException(AuthzErrorCode.ACCESS_REVIEW_RUNNING, "review " + reviewId + " has an open round");
        }
        Instant now = clock.instant();
        AccessReviewRound round = rounds.saveAndFlush(AccessReviewRound.open(reviewId, actorId, now, now.plus(Duration.ofDays(review.getDurationDays()))));
        // Changing the review raises its version, so two nodes starting the same round cannot both commit.
        review.started(now, scheduled);
        reviews.saveAndFlush(review);
        List<Long> roleIds = reviewRoles.findByReviewIdIn(List.of(reviewId)).stream().map(AccessReviewRole::getRoleId).toList();
        Map<Long, Role> reviewed = roles.findAllById(roleIds).stream().collect(Collectors.toMap(Role::requireId, Function.identity()));
        List<RoleAssignment> found = new ArrayList<>();
        reviewed.keySet().stream().sorted().forEach(roleId -> found.addAll(assignments.findByRole(roleId)));
        Set<Long> protectedAccounts = systemAccounts(found.stream().filter(assignment -> isSystemRoleOfUser(reviewed.get(assignment.getRoleId()), assignment))
                .map(RoleAssignment::getSubjectId).collect(Collectors.toSet()));
        long roundId = round.requireId();
        items.saveAll(found.stream().filter(assignment -> !(isSystemRoleOfUser(reviewed.get(assignment.getRoleId()), assignment)
                && protectedAccounts.contains(assignment.getSubjectId()))).map(assignment -> AccessReviewItem.of(roundId, assignment)).toList());
        items.flush();
        audit.recordWithChange(new AuditRecord(AuditAction.ACCESS_REVIEW_STARTED, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(roundId), Long.toString(reviewId)));
        return round;
    }

    /** Applies a round's decisions and the review's fallback, then closes it. */
    // One audit event per removed assignment is the point of the loop.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private AccessReviewRound completeRound(@Nullable Long actorId, long roundId)
    {
        AccessReviewRound round = rounds.findForUpdate(roundId).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no round " + roundId));
        requireOpen(round);
        // The review is there: deleting it deletes its rounds.
        ReviewFallback fallback = requireReview(round.getReviewId()).getUnreviewed();
        List<AccessReviewItem> reviewed = items.findByRoundIdOrderByIdAsc(roundId);
        Map<Long, RoleAssignment> current = new HashMap<>();
        InClauseBatcher.query(reviewed.stream().map(AccessReviewItem::getAssignmentId).collect(Collectors.toSet()), assignments::findAllById)
                .forEach(assignment -> current.put(assignment.requireId(), assignment));
        Map<Long, Role> named = roles.findAllById(reviewed.stream().map(AccessReviewItem::getRoleId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Role::requireId, Function.identity()));
        long tenantId = TenantContext.requireTenantId();
        int revoked = 0;
        for (AccessReviewItem item : reviewed) {
            boolean revoke = item.getDecision() == ReviewDecision.REVOKE
                    || item.getDecision() == ReviewDecision.PENDING && fallback == ReviewFallback.REVOKE;
            RoleAssignment assignment = current.get(item.getAssignmentId());
            if (!revoke) {
                item.apply(ReviewOutcome.KEPT);
            }
            else if (assignment == null) {
                item.apply(ReviewOutcome.GONE);
            }
            else {
                assignments.delete(assignment);
                item.apply(ReviewOutcome.REVOKED);
                revoked++;
                Role role = named.get(item.getRoleId());
                audit.recordWithChange(new AuditRecord(AuditAction.ACCESS_REVIEW_REVOKED, AuditOutcome.SUCCESS, tenantId, actorId, null,
                        item.getSubjectType() + ":" + item.getSubjectId(), role == null ? Long.toString(item.getRoleId()) : role.getCode()));
            }
        }
        assignments.flush();
        items.saveAllAndFlush(reviewed);
        round.end(ReviewRoundStatus.COMPLETED, actorId, clock.instant());
        rounds.saveAndFlush(round);
        audit.recordWithChange(new AuditRecord(AuditAction.ACCESS_REVIEW_COMPLETED, AuditOutcome.SUCCESS, tenantId, actorId, null,
                Long.toString(roundId), "revoked " + revoked));
        return round;
    }

    /** Nobody reviews an assignment that gives them the role: directly, or through their group, department or position. */
    private void requireNotOwn(long reviewerId, List<AccessReviewItem> chosen)
    {
        Map<Long, RoleAssignment> current = new HashMap<>();
        assignments.findAllById(chosen.stream().filter(item -> item.getSubjectType() == SubjectType.ORG_UNIT).map(AccessReviewItem::getAssignmentId)
                .toList()).forEach(assignment -> current.put(assignment.requireId(), assignment));
        for (AccessReviewItem item : chosen) {
            RoleAssignment assignment = current.get(item.getAssignmentId());
            boolean subUnits = assignment != null && assignment.getTerms().includeSubUnits();
            if (holders.ofSubject(item.getSubjectType(), item.getSubjectId(), subUnits).contains(reviewerId)) {
                String name = subjects.find(item.getSubjectType(), item.getSubjectId()).map(Subject::name).orElse(Long.toString(item.getSubjectId()));
                throw new GrantForgeException(AuthzErrorCode.ACCESS_REVIEW_SELF, "account " + reviewerId + " reviews its own access", name);
            }
        }
    }

    /** As when removing assignments by hand, only platform administrators take the platform administrator role away. */
    private void requireRemovable(long reviewerId, List<AccessReviewItem> chosen)
    {
        Set<Long> platformRoles = roles.findAllById(chosen.stream().map(AccessReviewItem::getRoleId).collect(Collectors.toSet())).stream()
                .filter(role -> role.getType() == RoleType.SYSTEM && SystemRole.PLATFORM_ADMIN.code().equals(role.getCode()))
                .map(Role::requireId).collect(Collectors.toSet());
        if (platformRoles.isEmpty()) {
            return;
        }
        boolean holds = effectiveRoles.of(reviewerId, clock.instant()).stream().anyMatch(held -> held.active() && platformRoles.contains(held.role().id()));
        if (!holds) {
            throw new GrantForgeException(AuthzErrorCode.ROLE_NOT_ASSIGNABLE, "only platform administrators revoke " + SystemRole.PLATFORM_ADMIN.code());
        }
    }

    private static boolean isSystemRoleOfUser(@Nullable Role role, RoleAssignment assignment)
    {
        return role != null && role.getType() == RoleType.SYSTEM && assignment.getSubjectType() == SubjectType.USER;
    }

    private Set<Long> systemAccounts(Set<Long> accountIds)
    {
        return InClauseBatcher.query(accountIds, accounts::findAllById).stream().filter(UserAccount::isSystemAccount).map(UserAccount::requireId)
                .collect(Collectors.toSet());
    }

    private void configure(AccessReview review, AccessReviewCommand command)
    {
        String name = Strings.blankToNull(command.name());
        if (name == null || name.length() > AccessReview.NAME_MAX) {
            throw invalid("name is required and at most " + AccessReview.NAME_MAX + " characters");
        }
        String description = Strings.blankToNull(command.description());
        if (description != null && description.length() > AccessReview.DESCRIPTION_MAX) {
            throw invalid("description is at most " + AccessReview.DESCRIPTION_MAX + " characters");
        }
        Set<Long> roleIds = command.roleIds();
        if (roleIds.isEmpty() || roleIds.size() > MAX_ROLES) {
            throw invalid("a review needs 1-" + MAX_ROLES + " roles");
        }
        int days = command.durationDays();
        if (days < 1 || days > AccessReview.MAX_DURATION_DAYS) {
            throw invalid("a round lasts 1-" + AccessReview.MAX_DURATION_DAYS + " days");
        }
        Integer interval = command.intervalDays();
        if (interval != null && (interval < days || interval > AccessReview.MAX_INTERVAL_DAYS)) {
            throw invalid("rounds repeat every " + days + "-" + AccessReview.MAX_INTERVAL_DAYS + " days, not more often than they last");
        }
        Set<Long> known = roles.findAllById(roleIds).stream().map(Role::requireId).collect(Collectors.toSet());
        for (long roleId : roleIds) {
            if (!known.contains(roleId)) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no role " + roleId);
            }
        }
        review.configure(name, description, days, interval, command.unreviewed(), command.enabled(), command.nextRunAt());
    }

    private void store(long reviewId, Set<Long> roleIds)
    {
        reviewRoles.saveAll(roleIds.stream().sorted().map(roleId -> AccessReviewRole.of(reviewId, roleId)).toList());
        reviewRoles.flush();
    }

    // The lists per review are what the loop builds.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private List<AccessReviewView> views(List<AccessReview> found)
    {
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> ids = found.stream().map(AccessReview::requireId).toList();
        Map<Long, List<Long>> roleIds = new HashMap<>();
        for (AccessReviewRole link : reviewRoles.findByReviewIdIn(ids)) {
            roleIds.computeIfAbsent(link.getReviewId(), key -> new ArrayList<>()).add(link.getRoleId());
        }
        Map<Long, RoleView> named = roles.findAllById(roleIds.values().stream().flatMap(List::stream).distinct().toList()).stream()
                .map(RoleView::from).collect(Collectors.toMap(RoleView::id, Function.identity()));
        Map<Long, ReviewRoundView> open = roundViews(rounds.findByReviewIdInAndStatus(ids, ReviewRoundStatus.OPEN)).stream()
                .collect(Collectors.toMap(ReviewRoundView::reviewId, Function.identity(), (first, second) -> first));
        return found.stream().map(review -> new AccessReviewView(review.requireId(), review.getName(), review.getDescription(),
                roleIds.getOrDefault(review.requireId(), List.of()).stream().map(named::get).filter(Objects::nonNull)
                        .sorted(Comparator.comparing(RoleView::name)).toList(),
                review.getDurationDays(), review.getIntervalDays(), review.getUnreviewed(), review.isEnabled(), review.getNextRunAt(),
                review.getLastStartedAt(), open.get(review.requireId()))).toList();
    }

    private ReviewRoundView roundView(AccessReviewRound round)
    {
        return roundViews(List.of(round)).get(0);
    }

    // The counts per round are what the loop builds.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private List<ReviewRoundView> roundViews(List<AccessReviewRound> found)
    {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<ReviewDecision, Long>> decided = new HashMap<>();
        Map<Long, Long> revoked = new HashMap<>();
        for (ReviewTally tally : items.tally(found.stream().map(AccessReviewRound::requireId).toList())) {
            decided.computeIfAbsent(tally.roundId(), key -> new EnumMap<>(ReviewDecision.class)).merge(tally.decision(), tally.count(), Long::sum);
            if (tally.outcome() == ReviewOutcome.REVOKED) {
                revoked.merge(tally.roundId(), tally.count(), Long::sum);
            }
        }
        Set<Long> people = new HashSet<>();
        for (AccessReviewRound round : found) {
            addIfPresent(people, round.getStartedBy());
            addIfPresent(people, round.getEndedBy());
        }
        Map<Long, Subject> names = subjects.names(SubjectType.USER, people);
        return found.stream().map(round -> {
            Map<ReviewDecision, Long> counts = decided.getOrDefault(round.requireId(), Map.of());
            long pending = counts.getOrDefault(ReviewDecision.PENDING, 0L);
            long keep = counts.getOrDefault(ReviewDecision.KEEP, 0L);
            long revoke = counts.getOrDefault(ReviewDecision.REVOKE, 0L);
            return new ReviewRoundView(round.requireId(), round.getReviewId(), round.getStatus(), round.getStartedAt(), round.getDueAt(),
                    nameOf(names, round.getStartedBy()), round.getEndedAt(), nameOf(names, round.getEndedBy()),
                    new ReviewProgress(pending + keep + revoke, pending, keep, revoke, revoked.getOrDefault(round.requireId(), 0L)));
        }).toList();
    }

    private List<ReviewItemView> itemViews(List<AccessReviewItem> found)
    {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, RoleView> named = roles.findAllById(found.stream().map(AccessReviewItem::getRoleId).collect(Collectors.toSet())).stream()
                .map(RoleView::from).collect(Collectors.toMap(RoleView::id, Function.identity()));
        Map<Long, RoleAssignment> current = new HashMap<>();
        assignments.findAllById(found.stream().map(AccessReviewItem::getAssignmentId).toList())
                .forEach(assignment -> current.put(assignment.requireId(), assignment));
        Map<SubjectType, Map<Long, Subject>> bySubject = new EnumMap<>(SubjectType.class);
        found.stream().collect(Collectors.groupingBy(AccessReviewItem::getSubjectType, Collectors.mapping(AccessReviewItem::getSubjectId,
                Collectors.toSet()))).forEach((type, ids) -> bySubject.put(type, subjects.names(type, ids)));
        Set<Long> deciders = new HashSet<>();
        found.forEach(item -> addIfPresent(deciders, item.getDecidedBy()));
        Map<Long, Subject> reviewers = subjects.names(SubjectType.USER, deciders);
        List<ReviewItemView> views = new ArrayList<>();
        for (AccessReviewItem item : found) {
            RoleView role = named.get(item.getRoleId());
            if (role == null) {
                // Deleting a role deletes its items; this one went in the meantime.
                continue;
            }
            Subject subject = bySubject.getOrDefault(item.getSubjectType(), Map.of()).get(item.getSubjectId());
            RoleAssignment assignment = current.get(item.getAssignmentId());
            views.add(new ReviewItemView(item.requireId(), role,
                    subject == null ? new Subject(item.getSubjectType(), item.getSubjectId(), Long.toString(item.getSubjectId()), null) : subject,
                    assignment != null, assignment == null ? null : assignment.getTerms().validFrom(),
                    assignment == null ? null : assignment.getTerms().validTo(), assignment != null && assignment.getTerms().includeSubUnits(), item.getDecision(), nameOf(reviewers, item.getDecidedBy()), item.getDecidedAt(),
                    item.getComment(), item.getOutcome()));
        }
        return views;
    }

    private static void addIfPresent(Set<Long> ids, @Nullable Long id)
    {
        if (id != null) {
            ids.add(id);
        }
    }

    private static @Nullable Subject nameOf(Map<Long, Subject> names, @Nullable Long id)
    {
        return id == null ? null : names.get(id);
    }

    private AccessReview requireReview(long id)
    {
        return reviews.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no access review " + id));
    }

    private AccessReviewRound requireRound(long id)
    {
        return rounds.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no round " + id));
    }

    private static void requireOpen(AccessReviewRound round)
    {
        if (round.getStatus() != ReviewRoundStatus.OPEN) {
            throw new GrantForgeException(AuthzErrorCode.ACCESS_REVIEW_CLOSED, "round " + round.getId() + " is " + round.getStatus(),
                    round.getStatus().name());
        }
    }

    private static String roleCodes(AccessReviewView view)
    {
        return view.roles().stream().map(RoleView::code).collect(Collectors.joining(","));
    }

    private void record(AuditAction action, long actorId, long target, @Nullable String reason)
    {
        audit.recordWithChange(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null, Long.toString(target), reason));
    }

    private static GrantForgeException invalid(String reason)
    {
        return new GrantForgeException(AuthzErrorCode.ACCESS_REVIEW_INVALID, "invalid access review: " + reason, reason);
    }
}
