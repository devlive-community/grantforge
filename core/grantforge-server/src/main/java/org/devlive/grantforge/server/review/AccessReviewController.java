// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.review;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.AccessReviewService;
import org.devlive.grantforge.authz.application.ReviewItemView;
import org.devlive.grantforge.authz.domain.ReviewDecision;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Access reviews (D-75): administrators set up which roles are reviewed and how often and run the rounds; reviewers
 * keep or revoke the assignments of open rounds.
 */
@RestController
public final class AccessReviewController
{
    private final AccessReviewService reviews;

    /**
     * Creates the controller.
     *
     * @param reviews manages the reviews
     */
    public AccessReviewController(AccessReviewService reviews)
    {
        this.reviews = requireNonNull(reviews, "reviews");
    }

    /**
     * Lists the reviews.
     *
     * @return the reviews by name, each with its open round
     */
    @RequirePermission("system.access-review.read")
    @GetMapping("/api/v1/access-reviews")
    public List<AccessReviewResponse> listAccessReviews()
    {
        return reviews.list().stream().map(AccessReviewResponse::from).toList();
    }

    /**
     * Adds a review.
     *
     * @param user the session's principal
     * @param body the review
     * @return the review
     */
    @RequirePermission("system.access-review.manage")
    @PostMapping("/api/v1/access-reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public AccessReviewResponse createAccessReview(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody AccessReviewRequest body)
    {
        return AccessReviewResponse.from(reviews.create(user.accountId(), body.command()));
    }

    /**
     * Changes a review; an open round keeps its assignments.
     *
     * @param user the session's principal
     * @param id the review
     * @param body the new values
     * @return the review
     */
    @RequirePermission("system.access-review.manage")
    @PutMapping("/api/v1/access-reviews/{id}")
    public AccessReviewResponse updateAccessReview(@AuthenticationPrincipal SessionUser user, @PathVariable String id, @Valid @RequestBody AccessReviewRequest body)
    {
        return AccessReviewResponse.from(reviews.update(user.accountId(), review(id), body.command()));
    }

    /**
     * Deletes a review with its rounds.
     *
     * @param user the session's principal
     * @param id the review
     */
    @RequirePermission("system.access-review.manage")
    @DeleteMapping("/api/v1/access-reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccessReview(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        reviews.delete(user.accountId(), review(id));
    }

    /**
     * Starts a round now.
     *
     * @param user the session's principal
     * @param id the review
     * @return the round
     */
    @RequirePermission("system.access-review.manage")
    @PostMapping("/api/v1/access-reviews/{id}/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewRoundResponse startAccessReview(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return ReviewRoundResponse.from(reviews.start(user.accountId(), review(id)));
    }

    /**
     * Lists a review's latest rounds.
     *
     * @param id the review
     * @return the rounds, newest first
     */
    @RequirePermission("system.access-review.read")
    @GetMapping("/api/v1/access-reviews/{id}/rounds")
    public List<ReviewRoundResponse> accessReviewRounds(@PathVariable String id)
    {
        return reviews.rounds(review(id)).stream().map(ReviewRoundResponse::from).toList();
    }

    /**
     * Lists a page of a round's assignments.
     *
     * @param id the round
     * @param decision the decisions to list, all if left out
     * @param page the 1-based page
     * @param size the page size
     * @return the assignments under review
     */
    @RequirePermission("system.access-review.read")
    @GetMapping("/api/v1/access-review-rounds/{id}/items")
    public PageResult<ReviewItemResponse> accessReviewItems(@PathVariable String id, @RequestParam(required = false) @Nullable List<ReviewDecision> decision,
            @RequestParam(required = false) @Nullable Integer page, @RequestParam(required = false) @Nullable Integer size)
    {
        PageResult<ReviewItemView> found = reviews.items(round(id), decision == null ? List.of() : decision, PageQuery.of(page, size));
        return new PageResult<>(found.items().stream().map(ReviewItemResponse::from).toList(), found.page(), found.size(), found.total());
    }

    /**
     * Keeps or revokes assignments of an open round, or takes decisions back.
     *
     * @param user the session's principal
     * @param id the round
     * @param body the items and the decision
     * @return the items as decided
     */
    @RequirePermission("system.access-review.decide")
    @PostMapping("/api/v1/access-review-rounds/{id}/decisions")
    public List<ReviewItemResponse> decideAccessReview(@AuthenticationPrincipal SessionUser user, @PathVariable String id, @Valid @RequestBody DecisionsRequest body)
    {
        return reviews.decide(user.accountId(), round(id), body.items(), body.chosen(), body.comment()).stream().map(ReviewItemResponse::from).toList();
    }

    /**
     * Completes an open round now: revoked assignments are removed.
     *
     * @param user the session's principal
     * @param id the round
     * @return the round
     */
    @RequirePermission("system.access-review.manage")
    @PostMapping("/api/v1/access-review-rounds/{id}/complete")
    public ReviewRoundResponse completeAccessReview(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return ReviewRoundResponse.from(reviews.complete(user.accountId(), round(id)));
    }

    /**
     * Ends an open round without applying its decisions.
     *
     * @param user the session's principal
     * @param id the round
     * @return the round
     */
    @RequirePermission("system.access-review.manage")
    @PostMapping("/api/v1/access-review-rounds/{id}/cancel")
    public ReviewRoundResponse cancelAccessReview(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return ReviewRoundResponse.from(reviews.cancel(user.accountId(), round(id)));
    }

    private static long review(String id)
    {
        return PathIds.parse(id, "access review");
    }

    private static long round(String id)
    {
        return PathIds.parse(id, "round");
    }
}
