// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ClientGrant;
import org.devlive.grantforge.authz.domain.ClientType;
import org.devlive.grantforge.authz.domain.OAuthClient;
import org.devlive.grantforge.authz.domain.OAuthClientRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * The OAuth clients of applications: how they sign users in and obtain tokens. Only accounts of the platform tenant change
 * clients, which all tenants share; callers need the matching permission, which the API checks. Secrets are generated here,
 * shown once and stored as hashes.
 */
@Service
public final class OAuthClientService
{
    /**
     * Scopes a client may ask for: OpenID Connect's, {@code permissions} to read its users' permissions, {@code catalog} to
     * declare its data entities with a token of its own.
     */
    public static final Set<String> SCOPES = Set.of("openid", "profile", "email", "permissions", "catalog");

    /** Most redirect URIs a client has. */
    public static final int MAX_REDIRECT_URIS = 10;

    /** Longest grace period of a rotated secret. */
    public static final Duration MAX_GRACE = Duration.ofDays(7);

    static final Duration MIN_ACCESS_TTL = Duration.ofMinutes(1);
    static final Duration MAX_ACCESS_TTL = Duration.ofHours(24);
    static final Duration MIN_REFRESH_TTL = Duration.ofHours(1);
    static final Duration MAX_REFRESH_TTL = Duration.ofDays(90);

    private static final int SECRET_BYTES = 32;
    private static final int ID_BYTES = 12;
    // The names a loopback redirect URI may use; nothing connects to them.
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]");

    private final OAuthClientRepository clients;
    private final ApplicationRepository applications;
    private final CatalogAccess access;
    private final PasswordEncoder encoder;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates the service.
     *
     * @param clients the clients
     * @param applications the applications clients belong to
     * @param access who may change the shared catalog
     * @param encoder hashes secrets
     * @param audit records every change
     * @param transactionManager opens transactions
     * @param events tells the authorization server when a client may no longer obtain tokens
     * @param clock the current time
     */
    public OAuthClientService(OAuthClientRepository clients, ApplicationRepository applications, CatalogAccess access, PasswordEncoder encoder,
            AuditLog audit, PlatformTransactionManager transactionManager, ApplicationEventPublisher events, Clock clock)
    {
        this.clients = requireNonNull(clients, "clients");
        this.applications = requireNonNull(applications, "applications");
        this.access = requireNonNull(access, "access");
        this.encoder = requireNonNull(encoder, "encoder");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.events = requireNonNull(events, "events");
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Lists an application's clients, oldest first.
     *
     * @param applicationId the application
     * @return the clients
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown application
     */
    public List<OAuthClientView> list(long applicationId)
    {
        Instant now = clock.instant();
        return requireNonNull(transactions.execute(status -> {
            requireApplication(applicationId);
            return clients.findByApplicationIdOrderByCreatedAtAscIdAsc(applicationId).stream()
                    .map(client -> OAuthClientView.from(client, now)).toList();
        }));
    }

    /**
     * Registers a client of an application.
     *
     * @param actorId the account asking
     * @param applicationId the application; not the console, which signs users in itself
     * @param type confidential or public
     * @param settings what the client may do
     * @return the client, with its secret if confidential
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} outside the platform tenant,
     *         {@link CommonErrorCode#NOT_FOUND} for an unknown application, or {@link AuthzErrorCode#CLIENT_INVALID}
     */
    public IssuedClient register(long actorId, long applicationId, ClientType type, ClientSettings settings)
    {
        access.requireEditor(actorId);
        Instant now = clock.instant();
        String secret = type == ClientType.CONFIDENTIAL ? token(SECRET_BYTES) : null;
        OAuthClientView view = audited(() -> {
            Application application = requireApplication(applicationId);
            List<FieldIssue> issues = new ArrayList<>();
            if (Application.CONSOLE.equals(application.getCode())) {
                issues.add(FieldIssue.of("applicationId", "error.client.console"));
            }
            check(type, settings, issues);
            OAuthClient client = OAuthClient.create(applicationId, "gf_" + token(ID_BYTES), type,
                    secret == null ? null : encoder.encode(secret), now);
            apply(client, settings);
            return OAuthClientView.from(clients.saveAndFlush(client), now);
        }, made -> record(AuditAction.CLIENT_CREATED, actorId, made, made.clientId()));
        return new IssuedClient(view, secret);
    }

    /**
     * Changes what a client may do; disabling it ends what it was issued ({@link ClientAccessEnded}).
     *
     * @param actorId the account asking
     * @param id the client's record
     * @param settings what it may do
     * @return the client
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} outside the platform tenant,
     *         {@link CommonErrorCode#NOT_FOUND} for an unknown client, or {@link AuthzErrorCode#CLIENT_INVALID}
     */
    public OAuthClientView update(long actorId, long id, ClientSettings settings)
    {
        access.requireEditor(actorId);
        Instant now = clock.instant();
        return audited(() -> {
            OAuthClient client = require(id);
            List<FieldIssue> issues = new ArrayList<>();
            check(client.getType(), settings, issues);
            boolean wasEnabled = client.isEnabled();
            apply(client, settings);
            if (wasEnabled && !client.isEnabled()) {
                events.publishEvent(new ClientAccessEnded(client.requireId(), client.getClientId()));
            }
            return OAuthClientView.from(clients.saveAndFlush(client), now);
        }, made -> record(AuditAction.CLIENT_UPDATED, actorId, made, made.clientId()));
    }

    /**
     * Gives a confidential client a new secret; the current one keeps working for a grace period.
     *
     * @param actorId the account asking
     * @param id the client's record
     * @param grace how long the current secret keeps working, at most {@link #MAX_GRACE}; zero ends it at once
     * @return the client with its new secret
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} outside the platform tenant,
     *         {@link CommonErrorCode#NOT_FOUND} for an unknown client, or {@link AuthzErrorCode#CLIENT_INVALID} for a public
     *         client or a grace period out of range
     */
    public IssuedClient rotateSecret(long actorId, long id, Duration grace)
    {
        access.requireEditor(actorId);
        if (grace.isNegative() || grace.compareTo(MAX_GRACE) > 0) {
            throw invalid(List.of(FieldIssue.of("graceHours", "error.client.grace", MAX_GRACE.toHours())));
        }
        Instant now = clock.instant();
        String secret = token(SECRET_BYTES);
        OAuthClientView view = audited(() -> {
            OAuthClient client = require(id);
            if (client.getType() != ClientType.CONFIDENTIAL) {
                throw invalid(List.of(FieldIssue.of("type", "error.client.public-no-secret")));
            }
            client.rotateSecret(encoder.encode(secret), now, grace);
            return OAuthClientView.from(clients.saveAndFlush(client), now);
        }, made -> record(AuditAction.CLIENT_SECRET_ROTATED, actorId, made, grace.toHours() + "h grace"));
        return new IssuedClient(view, secret);
    }

    /**
     * Deletes a client and, through {@link ClientAccessEnded}, what the authorization server issued to it.
     *
     * @param actorId the account asking
     * @param id the client's record
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} outside the platform tenant or
     *         {@link CommonErrorCode#NOT_FOUND} for an unknown client
     */
    public void delete(long actorId, long id)
    {
        access.requireEditor(actorId);
        Instant now = clock.instant();
        audited(() -> {
            OAuthClient client = require(id);
            OAuthClientView view = OAuthClientView.from(client, now);
            events.publishEvent(new ClientAccessEnded(client.requireId(), client.getClientId()));
            clients.delete(client);
            return view;
        }, made -> record(AuditAction.CLIENT_DELETED, actorId, made, made.clientId()));
    }

    private static void check(ClientType type, ClientSettings settings, List<FieldIssue> issues)
    {
        if (settings.name().isBlank() || settings.name().strip().length() > Application.NAME_MAX) {
            issues.add(FieldIssue.of("name", "error.client.name", Application.NAME_MAX));
        }
        if (settings.redirectUris().size() > MAX_REDIRECT_URIS) {
            issues.add(FieldIssue.of("redirectUris", "error.client.redirect-count", MAX_REDIRECT_URIS));
        }
        for (int i = 0; i < settings.redirectUris().size(); i++) {
            if (!redirectAllowed(settings.redirectUris().get(i))) {
                issues.add(FieldIssue.of("redirectUris[" + i + "]", "error.client.redirect-invalid"));
            }
        }
        if (settings.scopes().stream().anyMatch(scope -> !SCOPES.contains(scope))) {
            issues.add(FieldIssue.of("scopes", "error.client.scope-unknown", String.join(", ", SCOPES.stream().sorted().toList())));
        }
        Set<ClientGrant> grants = settings.grants();
        if (grants.isEmpty()) {
            issues.add(FieldIssue.of("grants", "error.client.grant-required"));
        }
        if (grants.contains(ClientGrant.AUTHORIZATION_CODE) && settings.redirectUris().isEmpty()) {
            issues.add(FieldIssue.of("redirectUris", "error.client.redirect-required"));
        }
        if (grants.contains(ClientGrant.REFRESH_TOKEN) && !grants.contains(ClientGrant.AUTHORIZATION_CODE)) {
            issues.add(FieldIssue.of("grants", "error.client.refresh-needs-code"));
        }
        if (grants.contains(ClientGrant.CLIENT_CREDENTIALS) && type == ClientType.PUBLIC) {
            issues.add(FieldIssue.of("grants", "error.client.credentials-need-secret"));
        }
        if (outside(settings.accessTokenTtl(), MIN_ACCESS_TTL, MAX_ACCESS_TTL)) {
            issues.add(FieldIssue.of("accessTokenMinutes", "error.client.ttl", MIN_ACCESS_TTL.toMinutes(), MAX_ACCESS_TTL.toMinutes()));
        }
        if (outside(settings.refreshTokenTtl(), MIN_REFRESH_TTL, MAX_REFRESH_TTL)) {
            issues.add(FieldIssue.of("refreshTokenHours", "error.client.ttl", MIN_REFRESH_TTL.toHours(), MAX_REFRESH_TTL.toHours()));
        }
        if (!issues.isEmpty()) {
            throw invalid(issues);
        }
    }

    /**
     * Whether users may be sent back to a URI: an absolute URI without fragment or wildcard, over HTTPS unless it is the
     * user's own machine, or a custom scheme of a native app.
     */
    static boolean redirectAllowed(String text)
    {
        if (text.isBlank() || text.contains("*") || text.length() > 512) {
            return false;
        }
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getFragment() != null) {
                return false;
            }
            String lower = scheme.toLowerCase(Locale.ROOT);
            if ("https".equals(lower)) {
                return uri.getHost() != null;
            }
            if ("http".equals(lower)) {
                return uri.getHost() != null && LOOPBACK.contains(uri.getHost().toLowerCase(Locale.ROOT));
            }
            // A native app's own scheme, such as com.example.app:/callback; never a scheme browsers run code with.
            return lower.contains(".") && !Set.of("javascript", "data", "file", "vbscript").contains(lower);
        }
        catch (URISyntaxException malformed) {
            return false;
        }
    }

    private static boolean outside(Duration value, Duration min, Duration max)
    {
        return value.compareTo(min) < 0 || value.compareTo(max) > 0;
    }

    private static void apply(OAuthClient client, ClientSettings settings)
    {
        client.configure(settings.name().strip(), List.copyOf(new LinkedHashSet<>(settings.redirectUris())), settings.scopes(),
                settings.grants().isEmpty() ? EnumSet.noneOf(ClientGrant.class) : EnumSet.copyOf(settings.grants()), settings.accessTokenTtl(), settings.refreshTokenTtl(), settings.enabled());
    }

    private String token(int bytes)
    {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static GrantForgeException invalid(List<FieldIssue> issues)
    {
        return new GrantForgeException(AuthzErrorCode.CLIENT_INVALID, issues.size() + " client issues").withFieldIssues(issues);
    }

    private Application requireApplication(long id)
    {
        return applications.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no application " + id));
    }

    private OAuthClient require(long id)
    {
        return clients.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no client " + id));
    }

    private void record(AuditAction action, long actorId, OAuthClientView client, String reason)
    {
        audit.recordWithChange(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(client.id()), reason));
    }

    /** Makes a change and records its event in one transaction, so neither happens without the other. */
    private <T> T audited(Supplier<T> change, Consumer<T> event)
    {
        return requireNonNull(transactions.execute(status -> {
            T made = change.get();
            event.accept(made);
            return made;
        }));
    }
}
