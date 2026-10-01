// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.ApiCatalogService;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.security.SessionUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** The API catalog: every endpoint the server serves and who may call it. */
@RestController
@RequestMapping("/api/v1/api-endpoints")
public final class ApiEndpointController
{
    private final ApiCatalogService catalog;

    /**
     * Creates the controller.
     *
     * @param catalog the API catalog
     */
    public ApiEndpointController(ApiCatalogService catalog)
    {
        this.catalog = requireNonNull(catalog, "catalog");
    }

    /**
     * Lists every endpoint ever found, by path and method; removed ones stay until reviewed and for history.
     *
     * @param user the session's principal
     * @return the endpoints
     */
    @RequirePermission("platform.catalog.read")
    @GetMapping
    public List<ApiEndpointResponse> list(@AuthenticationPrincipal SessionUser user)
    {
        return catalog.list(user.accountId()).stream().map(ApiEndpointResponse::from).toList();
    }

    /**
     * Confirms the changes of endpoints since the last review.
     *
     * @param user the session's principal
     * @param body the endpoints
     * @return how many changes were confirmed
     */
    @RequirePermission("platform.api.review")
    @PostMapping("/review")
    public ApiReviewResponse review(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody ApiReviewRequest body)
    {
        return new ApiReviewResponse(catalog.review(user.accountId(), body.ids()));
    }
}
