// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.sod;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.SodService;
import org.devlive.grantforge.common.security.RequirePermission;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** Separation of duties (D-73): constraints on roles held together and the accounts that conflict with them. */
@RestController
public final class SodController
{
    private final SodService sod;

    /**
     * Creates the controller.
     *
     * @param sod manages the constraints
     */
    public SodController(SodService sod)
    {
        this.sod = requireNonNull(sod, "sod");
    }

    /**
     * Lists the constraints.
     *
     * @return the constraints by name
     */
    @RequirePermission("system.sod.read")
    @GetMapping("/api/v1/sod-constraints")
    public List<SodConstraintResponse> list()
    {
        return sod.list().stream().map(SodConstraintResponse::from).toList();
    }

    /**
     * Adds a constraint.
     *
     * @param user the session's principal
     * @param body the constraint
     * @return the constraint
     */
    @RequirePermission("system.sod.create")
    @PostMapping("/api/v1/sod-constraints")
    @ResponseStatus(HttpStatus.CREATED)
    public SodConstraintResponse create(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody SodConstraintRequest body)
    {
        return SodConstraintResponse.from(sod.create(user.accountId(), body.command()));
    }

    /**
     * Changes a constraint; its code stays.
     *
     * @param user the session's principal
     * @param id the constraint
     * @param body the new values
     * @return the constraint
     */
    @RequirePermission("system.sod.update")
    @PutMapping("/api/v1/sod-constraints/{id}")
    public SodConstraintResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id, @Valid @RequestBody SodConstraintRequest body)
    {
        return SodConstraintResponse.from(sod.update(user.accountId(), PathIds.parse(id, "constraint"), body.command()));
    }

    /**
     * Deletes a constraint.
     *
     * @param user the session's principal
     * @param id the constraint
     */
    @RequirePermission("system.sod.delete")
    @DeleteMapping("/api/v1/sod-constraints/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        sod.delete(user.accountId(), PathIds.parse(id, "constraint"));
    }

    /**
     * Lists every account that holds more of an enabled constraint's roles than it allows.
     *
     * @return the conflicts by constraint and account
     */
    @RequirePermission("system.sod.read")
    @GetMapping("/api/v1/sod-conflicts")
    public List<SodConflictResponse> conflicts()
    {
        return sod.conflicts().stream().map(SodConflictResponse::from).toList();
    }
}
