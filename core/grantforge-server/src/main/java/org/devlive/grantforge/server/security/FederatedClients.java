// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.identity.application.IdentitySourceSettings;
import org.devlive.grantforge.identity.application.OidcSettings;
import org.devlive.grantforge.identity.domain.IdentitySource;
import org.devlive.grantforge.identity.domain.IdentitySourceRepository;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * The OpenID Connect providers of identity sources as Spring Security client registrations (D-72), one per source code.
 * A provider is described by its issuer's discovery document, read once and kept until the source changes; a source
 * without a client secret is a public client, which PKCE protects.
 */
@Component
public class FederatedClients
        implements ClientRegistrationRepository
{
    /** Where providers send users back, followed by the source's code. */
    static final String CALLBACK = "/api/v1/auth/federated/callback/";

    private static final Logger LOG = LoggerFactory.getLogger(FederatedClients.class);

    private final IdentitySourceRepository sources;
    private final IdentitySourceSettings settings;
    private final Map<String, Cached> registrations = new ConcurrentHashMap<>();

    /**
     * Creates the repository.
     *
     * @param sources the identity sources
     * @param settings reads their settings and secrets
     */
    public FederatedClients(IdentitySourceRepository sources, IdentitySourceSettings settings)
    {
        this.sources = requireNonNull(sources, "sources");
        this.settings = requireNonNull(settings, "settings");
    }

    @Override
    public @Nullable ClientRegistration findByRegistrationId(String registrationId)
    {
        IdentitySource source = provider(registrationId).orElse(null);
        if (source == null) {
            return null;
        }
        Object stamp = Objects.hash(source.getVersion(), source.getUpdatedAt());
        Cached cached = registrations.get(registrationId);
        if (cached != null && cached.stamp().equals(stamp)) {
            return cached.registration();
        }
        OidcSettings oidc = settings.oidc(source);
        String secret = settings.secret(source);
        try {
            ClientRegistration registration = ClientRegistrations.fromIssuerLocation(oidc.issuer())
                    .registrationId(registrationId)
                    .clientName(source.getName())
                    .clientId(oidc.clientId())
                    .clientSecret(secret)
                    .clientAuthenticationMethod(secret == null ? ClientAuthenticationMethod.NONE : ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .scope(oidc.scopes().split("\\s+"))
                    .redirectUri("{baseUrl}" + CALLBACK + "{registrationId}")
                    .userNameAttributeName(IdTokenClaimNames.SUB)
                    .build();
            registrations.put(registrationId, new Cached(stamp, registration));
            return registration;
        }
        catch (RuntimeException unreachable) {
            // A provider that does not answer cannot sign anyone in; the sign-in page reports it.
            LOG.warn("Identity source '{}' could not read the discovery document of {}: {}", registrationId, oidc.issuer(),
                    unreachable.getMessage());
            return null;
        }
    }

    /**
     * Returns the enabled provider with a code.
     *
     * @param code the source's code
     * @return the source, if it is an enabled OpenID Connect provider
     */
    Optional<IdentitySource> provider(String code)
    {
        return TenantContext.callAsSystem(() -> sources.findByCode(code))
                .filter(source -> source.isEnabled() && source.getType() == IdentitySourceType.OIDC);
    }

    /**
     * Returns the settings of an enabled provider.
     *
     * @param code the source's code
     * @return the settings, if it is an enabled OpenID Connect provider
     */
    Optional<OidcSettings> settings(String code)
    {
        return provider(code).map(settings::oidc);
    }

    /** A registration with the state of the source it was made from. */
    private record Cached(Object stamp, ClientRegistration registration)
    {
    }
}
