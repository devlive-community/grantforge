// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ClientGrant;
import org.devlive.grantforge.authz.domain.ClientType;
import org.devlive.grantforge.authz.domain.OAuthClient;
import org.devlive.grantforge.authz.domain.OAuthClientRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, OAuthClientService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RecordApplicationEvents
class OAuthClientServiceTest
{
    private static final ClientSettings WEB = new ClientSettings(" CRM web ", List.of("https://crm.example/cb", "https://crm.example/cb"),
            Set.of("openid", "profile"), Set.of(ClientGrant.AUTHORIZATION_CODE, ClientGrant.REFRESH_TOKEN), Duration.ofMinutes(15),
            Duration.ofDays(30), true);

    @Autowired
    private OAuthClientService service;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private OAuthClientRepository clients;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEvents published;

    @MockitoBean
    private PlatformAdministrators platform;

    private CatalogFixture fixture;

    private long crm;

    @BeforeEach
    void createAccounts()
    {
        fixture = new CatalogFixture(tenants, accounts, platform);
        crm = applications.save(Application.create("crm", "CRM", null)).requireId();
    }

    @AfterEach
    void deleteRows()
    {
        clients.deleteAllInBatch();
        fixture.deleteRows(resources, applications, events, transactionManager);
    }

    @Test
    void registersClientsShowingTheSecretOnceAndAuditsEveryChange()
    {
        IssuedClient issued = fixture.asRoot(() -> service.register(fixture.root, crm, ClientType.CONFIDENTIAL, WEB));
        String clientId = issued.client().clientId();

        assertThat(clientId).startsWith("gf_");
        assertThat(issued.secret()).isNotNull().hasSize(43);
        assertThat(issued.toString()).doesNotContain(issued.secret());
        assertThat(issued.client().name()).isEqualTo("CRM web");
        assertThat(issued.client().redirectUris()).containsExactly("https://crm.example/cb");
        OAuthClient stored = clients.findByClientId(clientId).orElseThrow();
        assertThat(encoder.matches(issued.secret(), stored.getSecretHash())).isTrue();

        ClientSettings narrower = new ClientSettings("CRM", List.of("http://localhost:5173/cb"), Set.of("openid"),
                Set.of(ClientGrant.AUTHORIZATION_CODE, ClientGrant.CLIENT_CREDENTIALS), Duration.ofHours(1), Duration.ofHours(1), false);
        OAuthClientView updated = fixture.asRoot(() -> service.update(fixture.root, issued.client().id(), narrower));
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.grants()).containsExactlyInAnyOrder(ClientGrant.AUTHORIZATION_CODE, ClientGrant.CLIENT_CREDENTIALS);

        IssuedClient rotated = fixture.asRoot(() -> service.rotateSecret(fixture.root, issued.client().id(), Duration.ofHours(2)));
        assertThat(rotated.secret()).isNotEqualTo(issued.secret());
        assertThat(rotated.client().previousSecretExpiresAt()).isNotNull();
        OAuthClient after = clients.findByClientId(clientId).orElseThrow();
        assertThat(encoder.matches(rotated.secret(), after.getSecretHash())).isTrue();

        IssuedClient spa = fixture.asRoot(() -> service.register(fixture.root, crm, ClientType.PUBLIC, WEB));
        assertThat(spa.secret()).isNull();
        assertThat(fixture.asRoot(() -> service.list(crm))).extracting(OAuthClientView::clientId)
                .containsExactly(clientId, spa.client().clientId());

        fixture.asRoot(() -> {
            service.delete(fixture.root, spa.client().id());
            return null;
        });
        assertThat(clients.findByClientId(spa.client().clientId())).isEmpty();
        // Disabling and deleting end what the authorization server issued; other changes do not.
        assertThat(published.stream(ClientAccessEnded.class)).containsExactly(
                new ClientAccessEnded(issued.client().id(), clientId),
                new ClientAccessEnded(spa.client().id(), spa.client().clientId()));
        assertThat(CatalogFixture.trail(events)).containsExactly(
                "CLIENT_CREATED:" + fixture.root + ":" + clientId,
                "CLIENT_UPDATED:" + fixture.root + ":" + clientId,
                "CLIENT_SECRET_ROTATED:" + fixture.root + ":2h grace",
                "CLIENT_CREATED:" + fixture.root + ":" + spa.client().clientId(),
                "CLIENT_DELETED:" + fixture.root + ":" + spa.client().clientId());
    }

    @Test
    void refusesWrongSettingsNamingEachField()
    {
        ClientSettings wrong = new ClientSettings(" ", List.of("https://*.example/cb", "http://crm.example/cb", "https://crm.example/cb#x"),
                Set.of("openid", "admin"), Set.of(ClientGrant.REFRESH_TOKEN, ClientGrant.CLIENT_CREDENTIALS), Duration.ofSeconds(30),
                Duration.ofDays(91), true);

        assertThatThrownBy(() -> fixture.asRoot(() -> service.register(fixture.root, crm, ClientType.PUBLIC, wrong)))
                .satisfies(error -> assertThat(issues(error)).containsExactly("name", "redirectUris[0]", "redirectUris[1]",
                        "redirectUris[2]", "scopes", "grants", "grants", "accessTokenMinutes", "refreshTokenHours"));
        ClientSettings noRedirect = new ClientSettings("CRM", List.of(), Set.of(), Set.of(ClientGrant.AUTHORIZATION_CODE),
                Duration.ofMinutes(15), Duration.ofDays(30), true);
        assertThatThrownBy(() -> fixture.asRoot(() -> service.register(fixture.root, crm, ClientType.PUBLIC, noRedirect)))
                .satisfies(error -> assertThat(issues(error)).containsExactly("redirectUris"));
        ClientSettings noGrant = new ClientSettings("CRM", List.of(), Set.of(), Set.of(), Duration.ofMinutes(15), Duration.ofDays(30), true);
        assertThatThrownBy(() -> fixture.asRoot(() -> service.register(fixture.root, crm, ClientType.PUBLIC, noGrant)))
                .satisfies(error -> assertThat(issues(error)).containsExactly("grants"));
        assertThat(clients.count()).isZero();
        assertThat(events.count()).isZero();
    }

    @Test
    void refusesTheConsoleUnknownRecordsPublicRotationAndLongGrace()
    {
        long console = applicationService.registerConsole();
        IssuedClient spa = fixture.asRoot(() -> service.register(fixture.root, crm, ClientType.PUBLIC, WEB));

        assertThatThrownBy(() -> fixture.asRoot(() -> service.register(fixture.root, console, ClientType.PUBLIC, WEB)))
                .satisfies(error -> assertThat(issues(error)).containsExactly("applicationId"));
        assertThatThrownBy(() -> fixture.asRoot(() -> service.register(fixture.root, 42, ClientType.PUBLIC, WEB)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> fixture.asRoot(() -> service.list(42)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> fixture.asRoot(() -> service.update(fixture.root, 42, WEB)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> fixture.asRoot(() -> service.rotateSecret(fixture.root, spa.client().id(), Duration.ZERO)))
                .satisfies(error -> assertThat(issues(error)).containsExactly("type"));
        assertThatThrownBy(() -> fixture.asRoot(() -> service.rotateSecret(fixture.root, spa.client().id(), Duration.ofDays(8))))
                .satisfies(error -> assertThat(issues(error)).containsExactly("graceHours"));
        assertThatThrownBy(() -> fixture.asRoot(() -> {
            applicationService.delete(fixture.root, crm);
            return null;
        })).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(AuthzErrorCode.APPLICATION_NOT_EMPTY));
    }

    @Test
    void onlyThePlatformChangesClients()
    {
        assertThatThrownBy(() -> fixture.inTenant(() -> service.register(fixture.boss, crm, ClientType.PUBLIC, WEB)))
                .satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.FORBIDDEN));
        assertThat(clients.count()).isZero();
    }

    @Test
    void acceptsAppSchemesButNotScriptOnes()
    {
        assertThat(OAuthClientService.redirectAllowed("com.example.app:/callback")).isTrue();
        assertThat(OAuthClientService.redirectAllowed("http://127.0.0.1:9000/cb")).isTrue();
        assertThat(OAuthClientService.redirectAllowed("http://[::1]/cb")).isTrue();
        assertThat(OAuthClientService.redirectAllowed("javascript:alert(1)")).isFalse();
        assertThat(OAuthClientService.redirectAllowed("/relative")).isFalse();
        assertThat(OAuthClientService.redirectAllowed("https:///nohost")).isFalse();
        assertThat(OAuthClientService.redirectAllowed("not a uri")).isFalse();
        assertThat(OAuthClientService.redirectAllowed("https://x.example/" + "a".repeat(520))).isFalse();
    }

    private static List<String> issues(Throwable error)
    {
        GrantForgeException failure = (GrantForgeException) error;
        assertThat(failure.getErrorCode()).isEqualTo(AuthzErrorCode.CLIENT_INVALID);
        return failure.getFieldIssues().stream().map(FieldIssue::field).toList();
    }
}
