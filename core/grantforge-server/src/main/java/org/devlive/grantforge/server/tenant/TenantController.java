// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.tenant;

import jakarta.validation.Valid;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.application.TenantService;
import org.devlive.grantforge.identity.application.TenantSummary;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Objects.requireNonNull;

/** Tenants, for platform administrators only. */
@RestController
@RequestMapping("/api/v1/tenants")
public final class TenantController
{
    private final TenantService tenants;

    /**
     * Creates the controller.
     *
     * @param tenants the tenant administration
     */
    public TenantController(TenantService tenants)
    {
        this.tenants = requireNonNull(tenants, "tenants");
    }

    /**
     * Lists tenants whose code or name contains a text, the platform tenant first.
     *
     * @param user the session's principal
     * @param q the text to look for; every tenant if omitted
     * @param page 1-based page number, 1 if omitted
     * @param size page size, 20 if omitted
     * @return the tenants
     */
    @GetMapping
    public PageResult<TenantResponse> list(@AuthenticationPrincipal SessionUser user,
            @RequestParam(required = false) @Nullable String q, @RequestParam(required = false) @Nullable Integer page,
            @RequestParam(required = false) @Nullable Integer size)
    {
        PageResult<TenantSummary> found = tenants.list(user.accountId(), q, PageQuery.of(page, size));
        return new PageResult<>(found.items().stream().map(TenantResponse::from).toList(), found.page(), found.size(),
                found.total());
    }

    /**
     * Returns one tenant.
     *
     * @param user the session's principal
     * @param id the tenant ID
     * @return the tenant
     */
    @GetMapping("/{id}")
    public TenantResponse find(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return TenantResponse.from(tenants.find(user.accountId(), PathIds.parse(id, "tenant")));
    }

    /**
     * Creates a tenant and its first administrator, who must choose a new password at the first sign-in.
     *
     * @param user the session's principal
     * @param body the tenant and administrator
     * @return the new tenant
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody TenantCreateRequest body)
    {
        return TenantResponse.from(tenants.create(user.accountId(), body.toCommand()));
    }

    /**
     * Renames a tenant.
     *
     * @param user the session's principal
     * @param id the tenant ID
     * @param body the new details
     * @return the tenant
     */
    @PutMapping("/{id}")
    public TenantResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody TenantUpdateRequest body)
    {
        return TenantResponse.from(tenants.rename(user.accountId(), PathIds.parse(id, "tenant"), body.name()));
    }

    /**
     * Suspends a tenant: its accounts can no longer sign in and their sessions end.
     *
     * @param user the session's principal
     * @param id the tenant ID
     * @return the tenant
     */
    @PostMapping("/{id}/suspend")
    public TenantResponse suspend(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return TenantResponse.from(tenants.suspend(user.accountId(), PathIds.parse(id, "tenant")));
    }

    /**
     * Lets a suspended tenant's accounts sign in again.
     *
     * @param user the session's principal
     * @param id the tenant ID
     * @return the tenant
     */
    @PostMapping("/{id}/activate")
    public TenantResponse activate(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return TenantResponse.from(tenants.activate(user.accountId(), PathIds.parse(id, "tenant")));
    }
}
