// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Objects.requireNonNull;

/** The signed-in user. */
@RestController
@RequestMapping("/api/v1/me")
public final class MeController
{
    private final ProfileService profiles;

    /**
     * Creates the controller.
     *
     * @param profiles reads the signed-in user
     */
    public MeController(ProfileService profiles)
    {
        this.profiles = requireNonNull(profiles, "profiles");
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
