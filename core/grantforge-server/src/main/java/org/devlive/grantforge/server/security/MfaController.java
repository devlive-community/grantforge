// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.devlive.grantforge.common.security.AuthenticatedEndpoint;
import org.devlive.grantforge.identity.application.MfaService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

import static java.util.Objects.requireNonNull;

/**
 * The signed-in user's two-step sign-in (D-71): setting up an authenticator app, recovery codes and turning it off.
 * Turning it off and new recovery codes need a code, so a session left open is not enough.
 */
@RestController
@AuthenticatedEndpoint
@RequestMapping("/api/v1/me/mfa")
public final class MfaController
{
    private final MfaService mfa;
    private final Clock clock;

    /**
     * Creates the controller.
     *
     * @param mfa manages the authenticators
     * @param clock the current time
     */
    public MfaController(MfaService mfa, Clock clock)
    {
        this.mfa = requireNonNull(mfa, "mfa");
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns whether the user signs in in two steps.
     *
     * @param user the session's principal
     * @return the status
     */
    @GetMapping
    public MfaStatusResponse status(@AuthenticationPrincipal SessionUser user)
    {
        return MfaStatusResponse.from(mfa.status(user.accountId()));
    }

    /**
     * Starts setting up an authenticator app, replacing one not confirmed yet.
     *
     * @param user the session's principal
     * @return the secret and its link
     */
    @PostMapping("/enroll")
    public MfaEnrollmentResponse enroll(@AuthenticationPrincipal SessionUser user)
    {
        return MfaEnrollmentResponse.from(mfa.enroll(user.accountId()));
    }

    /**
     * Confirms the authenticator with a code from it, which turns two-step sign-in on; the code also counts as a fresh
     * second factor of this session.
     *
     * @param user the session's principal
     * @param body a code of the authenticator
     * @param request the request, whose session records the second factor
     * @return the recovery codes, shown this once
     */
    @PostMapping("/confirm")
    public RecoveryCodesResponse confirm(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody MfaCodeRequest body,
            HttpServletRequest request)
    {
        RecoveryCodesResponse codes = new RecoveryCodesResponse(mfa.confirm(user.accountId(), String.valueOf(body.code())));
        StepUpGuard.verified(request.getSession(), clock);
        return codes;
    }

    /**
     * Replaces the recovery codes; the old ones stop working.
     *
     * @param user the session's principal
     * @param body a second factor
     * @return the new codes, shown this once
     */
    @PostMapping("/recovery-codes")
    public RecoveryCodesResponse renewRecoveryCodes(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody MfaCodeRequest body)
    {
        return new RecoveryCodesResponse(mfa.renewRecoveryCodes(user.accountId(), String.valueOf(body.code())));
    }

    /**
     * Turns two-step sign-in off.
     *
     * @param user the session's principal
     * @param body a second factor
     */
    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody MfaCodeRequest body)
    {
        mfa.disable(user.accountId(), String.valueOf(body.code()));
    }
}
