// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.samples.notes;

import org.devlive.grantforge.sdk.AccessTokenResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Users sign in through GrantForge with Spring Security's OAuth 2.0 login, with PKCE, which GrantForge asks of every
 * client. The starter reads the signed-in user's access token from Spring Security's authorized clients.
 */
@Configuration(proxyBeanMethods = false)
public class NotesSecurity
{
    /**
     * The filter chain: the home page is open, everything else needs a sign-in.
     *
     * @param http the builder
     * @param registrations the OAuth clients, GrantForge's
     * @return the chain
     * @throws Exception if the configuration is invalid
     */
    @Bean
    public SecurityFilterChain notesFilterChain(HttpSecurity http, ClientRegistrationRepository registrations) throws Exception
    {
        DefaultOAuth2AuthorizationRequestResolver requests = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        requests.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        http.authorizeHttpRequests(authorize -> authorize.requestMatchers("/", "/error").permitAll().anyRequest().authenticated())
                .oauth2Login(login -> login.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(requests))
                        .defaultSuccessUrl("/", true));
        return http.build();
    }

    /**
     * Gives the starter the signed-in user's access token, from the session's authorized client.
     *
     * @param clients the authorized clients
     * @return the resolver
     */
    @Bean
    public AccessTokenResolver accessTokenResolver(OAuth2AuthorizedClientService clients)
    {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof OAuth2AuthenticationToken signedIn)) {
                return null;
            }
            OAuth2AuthorizedClient client = clients.loadAuthorizedClient(signedIn.getAuthorizedClientRegistrationId(), signedIn.getName());
            return client == null ? null : client.getAccessToken().getTokenValue();
        };
    }
}
