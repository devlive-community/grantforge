// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import jakarta.validation.Valid;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.common.security.RequireStepUp;
import org.devlive.grantforge.identity.application.IdentitySourceService;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * The identity sources of the tenant (D-72). Adding, changing and deleting one decides who can sign in, so they need a
 * fresh second factor from users who have one.
 */
@RestController
@RequestMapping("/api/v1/identity-sources")
public final class IdentitySourceController
{
    private final IdentitySourceService sources;

    /**
     * Creates the controller.
     *
     * @param sources manages the sources
     */
    public IdentitySourceController(IdentitySourceService sources)
    {
        this.sources = requireNonNull(sources, "sources");
    }

    /**
     * Lists the sources.
     *
     * @return the sources in creation order
     */
    @RequirePermission("system.identity-source.read")
    @GetMapping
    public List<IdentitySourceResponse> list()
    {
        return sources.list().stream().map(IdentitySourceResponse::from).toList();
    }

    /**
     * Returns a source.
     *
     * @param id the source
     * @return the source
     */
    @RequirePermission("system.identity-source.read")
    @GetMapping("/{id}")
    public IdentitySourceResponse find(@PathVariable String id)
    {
        return IdentitySourceResponse.from(sources.get(source(id)));
    }

    /**
     * Adds a source.
     *
     * @param user the session's principal
     * @param body the source
     * @return the source
     */
    @RequirePermission("system.identity-source.create")
    @RequireStepUp
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdentitySourceResponse create(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody IdentitySourceRequest body)
    {
        return IdentitySourceResponse.from(sources.create(user.accountId(), body.command()));
    }

    /**
     * Changes a source; its code and type stay.
     *
     * @param user the session's principal
     * @param id the source
     * @param body the new values
     * @return the source
     */
    @RequirePermission("system.identity-source.update")
    @RequireStepUp
    @PutMapping("/{id}")
    public IdentitySourceResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody IdentitySourceRequest body)
    {
        return IdentitySourceResponse.from(sources.update(user.accountId(), source(id), body.command()));
    }

    /**
     * Deletes a source no account signs in with.
     *
     * @param user the session's principal
     * @param id the source
     */
    @RequirePermission("system.identity-source.delete")
    @RequireStepUp
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        sources.delete(user.accountId(), source(id));
    }

    /**
     * Checks that a directory answers with the stored settings.
     *
     * @param id the source
     */
    @RequirePermission("system.identity-source.update")
    @PostMapping("/{id}/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void test(@PathVariable String id)
    {
        sources.test(source(id));
    }

    /**
     * Syncs the users of a directory into accounts now.
     *
     * @param user the session's principal
     * @param id the source
     * @return what changed
     */
    @RequirePermission("system.identity-source.sync")
    @PostMapping("/{id}/sync")
    public SyncReportResponse sync(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return SyncReportResponse.from(sources.sync(user.accountId(), source(id)));
    }

    private static long source(String id)
    {
        return PathIds.parse(id, "identity source");
    }
}
