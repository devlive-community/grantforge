// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

import org.devlive.grantforge.oauth.application.ClientSecrets;
import org.devlive.grantforge.oauth.application.TokenClaims;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * The authorization server's endpoints (D-65): authorization, token, revocation, introspection, key set, discovery,
 * UserInfo and logout, in a filter chain of their own ahead of the console's. Users sign in through the console, whose
 * session the authorization endpoint shares; clients authenticate with their secret (the previous one during a rotation's
 * grace period) or, if public, with PKCE alone.
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationServerSecurity
{
    /**
     * The filter chain of the authorization server's endpoints.
     *
     * @param http the builder
     * @param contexts the console's session-backed security contexts
     * @param claims what tokens and the UserInfo endpoint say about accounts
     * @param encoder the encoder client secrets are hashed with
     * @return the chain
     * @throws Exception if the configuration is invalid
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain authorizationServerFilterChain(HttpSecurity http, SecurityContextRepository contexts, TokenClaims claims,
            PasswordEncoder encoder) throws Exception
    {
        ClientSecrets secrets = new ClientSecrets(encoder);
        http.oauth2AuthorizationServer(server -> {
            http.securityMatcher(server.getEndpointsMatcher());
            server.oidc(oidc -> oidc.userInfoEndpoint(userInfo -> userInfo.userInfoMapper(claims::userInfo)))
                    .clientAuthentication(clients -> clients.authenticationProviders(providers -> providers.forEach(provider -> {
                        if (provider instanceof ClientSecretAuthenticationProvider secret) {
                            secret.setPasswordEncoder(secrets);
                        }
                    })));
        })
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .securityContext(context -> context.securityContextRepository(contexts))
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(new ConsoleSignIn(),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
