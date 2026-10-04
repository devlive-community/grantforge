// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.TenantStatus;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * What tokens and the UserInfo endpoint say about an account: its tenant ({@value #TENANT}) and login name, and with the
 * {@code profile} and {@code email} scopes its name and email address. Before any token is issued or refreshed for an
 * account, the account must still be able to sign in: enabled, not locked, without a pending password change, in an
 * active tenant. A disabled account therefore loses its applications at the next refresh.
 */
@Component
public final class TokenClaims
        implements OAuth2TokenCustomizer<JwtEncodingContext>
{
    /** Claim naming the account's tenant. */
    public static final String TENANT = "tid";

    private final OAuthPrincipals principals;
    private final UserAccountRepository accounts;
    private final TenantRepository tenants;
    private final Clock clock;

    /**
     * Creates the customizer.
     *
     * @param principals translates sign-ins
     * @param accounts the accounts tokens are for
     * @param tenants their tenants
     * @param clock the current time, for lockouts
     */
    public TokenClaims(OAuthPrincipals principals, UserAccountRepository accounts, TenantRepository tenants, Clock clock)
    {
        this.principals = requireNonNull(principals, "principals");
        this.accounts = requireNonNull(accounts, "accounts");
        this.tenants = requireNonNull(tenants, "tenants");
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Adds the account's claims to a token about to be signed; tokens a client obtains for itself get none.
     *
     * @param context the token being made
     * @throws OAuth2AuthenticationException with {@code invalid_grant} if the account may no longer sign in
     */
    @Override
    public void customize(JwtEncodingContext context)
    {
        Authentication principal = context.getPrincipal();
        OAuthSubject subject = principal == null ? null : principals.subjectOf(principal).orElse(null);
        if (subject == null) {
            return;
        }
        UserAccount account = signedIn(subject, OAuth2ErrorCodes.INVALID_GRANT);
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        Map<String, Object> claims = claims(subject, account, context.getAuthorizedScopes(), idToken);
        context.getClaims().claims(values -> values.putAll(claims));
    }

    /**
     * Answers the UserInfo endpoint.
     *
     * @param context the request, with the authorization of its access token
     * @return the claims the token's scopes allow
     * @throws OAuth2AuthenticationException with {@code invalid_token} if the token is not an account's or the account
     *         may no longer sign in
     */
    public OidcUserInfo userInfo(OidcUserInfoAuthenticationContext context)
    {
        OAuth2Authorization authorization = context.getAuthorization();
        Authentication signedIn = authorization.getAttribute(Principal.class.getName());
        OAuthSubject subject = Optional.ofNullable(signedIn).flatMap(principals::subjectOf)
                .orElseThrow(() -> refused(OAuth2ErrorCodes.INVALID_TOKEN));
        UserAccount account = signedIn(subject, OAuth2ErrorCodes.INVALID_TOKEN);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", Long.toString(subject.accountId()));
        claims.putAll(claims(subject, account, authorization.getAuthorizedScopes(), true));
        return new OidcUserInfo(claims);
    }

    private static Map<String, Object> claims(OAuthSubject subject, UserAccount account, Set<String> scopes, boolean aboutPerson)
    {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(TENANT, Long.toString(subject.tenantId()));
        claims.put("preferred_username", account.getUsername());
        if (aboutPerson && scopes.contains(OidcScopes.PROFILE)) {
            claims.put("name", requireNonNullElse(account.getDisplayName(), account.getUsername()));
        }
        String email = account.getEmail();
        if (aboutPerson && scopes.contains(OidcScopes.EMAIL) && email != null) {
            claims.put("email", email);
            claims.put("email_verified", false);
        }
        return claims;
    }

    private UserAccount signedIn(OAuthSubject subject, String error)
    {
        Tenant tenant = tenants.findById(subject.tenantId()).orElse(null);
        UserAccount account = TenantContext.callInTenant(subject.tenantId(), () -> accounts.findById(subject.accountId())).orElse(null);
        if (tenant == null || tenant.getStatus() != TenantStatus.ACTIVE || !mayUse(account)) {
            throw refused(error);
        }
        return requireNonNull(account, "account");
    }

    private boolean mayUse(@Nullable UserAccount account)
    {
        return account != null && account.getStatus() == AccountStatus.ACTIVE && !account.isLocked(clock.instant())
                && !account.isMustChangePassword();
    }

    private static OAuth2AuthenticationException refused(String error)
    {
        return new OAuth2AuthenticationException(new OAuth2Error(error, "The account can no longer sign in", null));
    }
}
