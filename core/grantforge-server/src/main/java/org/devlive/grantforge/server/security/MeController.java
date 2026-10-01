// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.ConsoleSessionService;
import org.devlive.grantforge.identity.application.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Objects.requireNonNull;

/** The signed-in user. */
@RestController
@RequestMapping("/api/v1/me")
public final class MeController
{
    private final ProfileService profiles;
    private final ConsoleSessionService sessions;

    /**
     * Creates the controller.
     *
     * @param profiles reads and changes the signed-in user
     * @param sessions ends the user's other sessions after a password change
     */
    public MeController(ProfileService profiles, ConsoleSessionService sessions)
    {
        this.profiles = requireNonNull(profiles, "profiles");
        this.sessions = requireNonNull(sessions, "sessions");
    }

    /**
     * Returns the signed-in user; the console calls it to restore a session after a reload.
     *
     * @param user the session's principal
     * @return the user
     */
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal SessionUser user)
    {
        return profiles.find(user.accountId()).map(MeResponse::from)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.UNAUTHENTICATED, "account no longer exists"));
    }

    /**
     * Changes the signed-in user's display name and e-mail address.
     *
     * @param user the session's principal
     * @param body the new values
     * @return the updated user
     */
    @PutMapping
    public MeResponse update(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody ProfileRequest body)
    {
        return MeResponse.from(profiles.update(user.accountId(), body.displayName(), body.email()));
    }

    /**
     * Changes the signed-in user's password. Every other session of the user ends, so a stolen session cannot
     * outlive the change; this one stays signed in.
     *
     * @param user the session's principal
     * @param body the current and the new password
     * @param request the request, whose session is kept
     */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody PasswordChangeRequest body,
            HttpServletRequest request)
    {
        profiles.changePassword(user.accountId(), body.currentPassword(), body.newPassword());
        HttpSession session = request.getSession(false);
        sessions.revokeOthers(user.accountId(), session == null ? null : session.getId());
        if (session != null) {
            PasswordChangeGuard.require(session, false);
        }
    }

    /**
     * Returns what the signed-in user may reach in the console. Roles arrive with the permission model; until
     * then every signed-in user reaches everything.
     *
     * @return the authorization snapshot
     */
    @GetMapping("/authorization")
    public AuthorizationResponse authorization()
    {
        return AuthorizationResponse.everything();
    }
}
