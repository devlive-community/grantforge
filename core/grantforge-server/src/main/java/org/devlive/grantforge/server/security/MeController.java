// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.devlive.grantforge.audit.application.AuditEntry;
import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.authz.application.AuthorizationEvaluator;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.common.security.AuthenticatedEndpoint;
import org.devlive.grantforge.identity.application.ConsoleSessionService;
import org.devlive.grantforge.identity.application.ProfileService;
import org.devlive.grantforge.identity.application.TenantService;
import org.devlive.grantforge.persistence.secured.FieldRules;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/** The signed-in user. */
@RestController
@AuthenticatedEndpoint
@RequestMapping("/api/v1/me")
public final class MeController
{
    private final ProfileService profiles;
    private final ConsoleSessionService sessions;
    private final AuditLog audit;
    private final TenantService tenants;
    private final AuthorizationEvaluator evaluator;
    private final FieldRules fields;

    /** What the login history shows of the audit trail. */
    private static final Set<AuditAction> LOGIN_ACTIONS = Set.of(AuditAction.LOGIN_SUCCEEDED, AuditAction.LOGIN_FAILED,
            AuditAction.ACCOUNT_LOCKED, AuditAction.LOGOUT, AuditAction.MFA_ENABLED, AuditAction.MFA_DISABLED,
            AuditAction.MFA_RECOVERY_CODES_RENEWED, AuditAction.MFA_RECOVERY_CODE_USED, AuditAction.MFA_STEP_UP);

    /**
     * Creates the controller.
     *
     * @param profiles reads and changes the signed-in user
     * @param sessions ends the user's other sessions after a password change
     * @param audit reads the login history
     * @param tenants tells platform administrators apart
     * @param evaluator works out what each user may reach
     * @param fields works out the secured fields each user does not see or change freely
     */
    public MeController(ProfileService profiles, ConsoleSessionService sessions, AuditLog audit, TenantService tenants,
            AuthorizationEvaluator evaluator, FieldRules fields)
    {
        this.fields = requireNonNull(fields, "fields");
        this.profiles = requireNonNull(profiles, "profiles");
        this.sessions = requireNonNull(sessions, "sessions");
        this.audit = requireNonNull(audit, "audit");
        this.tenants = requireNonNull(tenants, "tenants");
        this.evaluator = requireNonNull(evaluator, "evaluator");
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
     * Returns the signed-in user's sign-ins, refused sign-ins (including other people's attempts with this user's
     * name), lockouts and sign-outs, newest first.
     *
     * @param user the session's principal
     * @param page 1-based page number, 1 if omitted
     * @param size page size, 20 if omitted
     * @return the history
     */
    @GetMapping("/login-history")
    public PageResult<LoginHistoryResponse> loginHistory(@AuthenticationPrincipal SessionUser user,
            @RequestParam(required = false) @Nullable Integer page, @RequestParam(required = false) @Nullable Integer size)
    {
        PageResult<AuditEntry> found = audit.history(user.accountId(), LOGIN_ACTIONS, PageQuery.of(page, size));
        return new PageResult<>(found.items().stream().map(LoginHistoryResponse::from).toList(), found.page(),
                found.size(), found.total());
    }

    /**
     * Returns what the signed-in user may reach in the console, worked out from all of their effective roles.
     *
     * @param user the session's principal
     * @return the authorization snapshot
     */
    @GetMapping("/authorization")
    public AuthorizationResponse authorization(@AuthenticationPrincipal SessionUser user)
    {
        return AuthorizationResponse.from(evaluator.snapshot(user.accountId()), tenants.isPlatformAdministrator(user.accountId()),
                fields.restricted(user.accountId()));
    }
}
