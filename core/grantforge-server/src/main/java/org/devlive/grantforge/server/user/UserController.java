// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.user;

import jakarta.validation.Valid;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.common.security.RequireStepUp;
import org.devlive.grantforge.identity.application.UserAdminService;
import org.devlive.grantforge.identity.application.UserFilter;
import org.devlive.grantforge.identity.application.UserSummary;
import org.devlive.grantforge.identity.domain.UserState;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Objects.requireNonNull;

/** Accounts of the administrator's tenant. */
@RestController
@RequestMapping("/api/v1/users")
public final class UserController
{
    private final UserAdminService users;

    /**
     * Creates the controller.
     *
     * @param users the account administration
     */
    public UserController(UserAdminService users)
    {
        this.users = requireNonNull(users, "users");
    }

    /**
     * Lists matching accounts, the newest first.
     *
     * @param user the session's principal
     * @param q text the login name, display name or e-mail address contains
     * @param state whether the accounts can sign in right now
     * @param unitId a department whose members to list
     * @param includeSubUnits whether members of its sub-departments are listed too; {@code true} if omitted
     * @param page 1-based page number, 1 if omitted
     * @param size page size, 20 if omitted
     * @return the accounts
     */
    @RequirePermission("system.user.read")
    @GetMapping
    public PageResult<UserResponse> list(@AuthenticationPrincipal SessionUser user,
            @RequestParam(required = false) @Nullable String q, @RequestParam(required = false) @Nullable UserState state,
            @RequestParam(required = false) @Nullable String unitId,
            @RequestParam(defaultValue = "true") boolean includeSubUnits,
            @RequestParam(required = false) @Nullable Integer page, @RequestParam(required = false) @Nullable Integer size)
    {
        Long unit = unitId == null || unitId.isBlank() ? null : PathIds.parse(unitId, "department");
        PageResult<UserSummary> found = users.search(user.accountId(), new UserFilter(q, state, unit, includeSubUnits),
                PageQuery.of(page, size));
        return new PageResult<>(found.items().stream().map(UserResponse::from).toList(), found.page(), found.size(),
                found.total());
    }

    /**
     * Returns an account with its departments.
     *
     * @param user the session's principal
     * @param id the account
     * @return the account
     */
    @RequirePermission("system.user.read")
    @GetMapping("/{id}")
    public UserDetailResponse find(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return UserDetailResponse.from(users.find(user.accountId(), account(id)));
    }

    /**
     * Creates an account, which must choose a new password at its first sign-in.
     *
     * @param user the session's principal
     * @param body the account
     * @return the new account
     */
    @RequirePermission("system.user.create")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDetailResponse create(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody UserCreateRequest body)
    {
        UserProfileRequest profile = requireNonNull(body.profile(), "profile");
        return UserDetailResponse.from(users.create(user.accountId(), body.username(), body.password(), profile.toInput()));
    }

    /**
     * Changes an account's details and departments.
     *
     * @param user the session's principal
     * @param id the account
     * @param body the new details and departments
     * @return the account
     */
    @RequirePermission("system.user.update")
    @PutMapping("/{id}")
    public UserDetailResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody UserProfileRequest body)
    {
        return UserDetailResponse.from(users.update(user.accountId(), account(id), body.toInput()));
    }

    /**
     * Lets an account sign in again.
     *
     * @param user the session's principal
     * @param id the account
     * @return the account
     */
    @RequirePermission("system.user.status")
    @PostMapping("/{id}/enable")
    public UserDetailResponse enable(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return UserDetailResponse.from(users.enable(user.accountId(), account(id)));
    }

    /**
     * Stops an account from signing in and ends its sessions.
     *
     * @param user the session's principal
     * @param id the account
     * @return the account
     */
    @RequirePermission("system.user.status")
    @PostMapping("/{id}/disable")
    public UserDetailResponse disable(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return UserDetailResponse.from(users.disable(user.accountId(), account(id)));
    }

    /**
     * Locks an account until it is unlocked and ends its sessions.
     *
     * @param user the session's principal
     * @param id the account
     * @return the account
     */
    @RequirePermission("system.user.status")
    @PostMapping("/{id}/lock")
    public UserDetailResponse lock(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return UserDetailResponse.from(users.lock(user.accountId(), account(id)));
    }

    /**
     * Lifts any lock of an account.
     *
     * @param user the session's principal
     * @param id the account
     * @return the account
     */
    @RequirePermission("system.user.status")
    @PostMapping("/{id}/unlock")
    public UserDetailResponse unlock(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return UserDetailResponse.from(users.unlock(user.accountId(), account(id)));
    }

    /**
     * Sets a new password, which must be changed at the next sign-in, and ends the account's sessions.
     *
     * @param user the session's principal
     * @param id the account
     * @param body the new password
     * @return the account
     */
    @RequireStepUp
    @RequirePermission("system.user.reset-password")
    @PostMapping("/{id}/password")
    public UserDetailResponse resetPassword(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody PasswordResetRequest body)
    {
        return UserDetailResponse.from(users.resetPassword(user.accountId(), account(id), body.password()));
    }

    /**
     * Turns two-step sign-in off for an account whose authenticator was lost, and ends its sessions.
     *
     * @param user the session's principal
     * @param id the account
     */
    @RequirePermission("system.user.mfa-reset")
    @RequireStepUp
    @PostMapping("/{id}/mfa/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetMfa(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        users.resetMfa(user.accountId(), account(id));
    }

    /**
     * Deletes an account with its sessions and departments.
     *
     * @param user the session's principal
     * @param id the account
     */
    @RequirePermission("system.user.delete")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        users.delete(user.accountId(), account(id));
    }

    private static long account(String id)
    {
        return PathIds.parse(id, "account");
    }
}
