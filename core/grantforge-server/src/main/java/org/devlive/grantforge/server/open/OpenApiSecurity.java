// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import org.devlive.grantforge.oauth.application.OpenApiTokens;
import org.devlive.grantforge.server.oauth.ClientOrigins;
import org.devlive.grantforge.server.security.ProblemSecurityHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Security of the open API (D-66): {@value #OPEN_API} answers only bearer access tokens of the authorization server, with
 * no session, CSRF token or anonymous access, so console sessions do not reach it and its tokens reach nothing else.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiSecurity
{
    /** Paths of the open API. */
    public static final String OPEN_API = "/api/v1/open/**";

    /**
     * The filter chain of the open API.
     *
     * @param http the builder
     * @param tokens recognises access tokens
     * @param resolver MVC's exception resolver, to render rejections as problem details
     * @param origins the origins browser applications may call from
     * @return the chain
     * @throws Exception if the configuration is invalid
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain openApiFilterChain(HttpSecurity http, OpenApiTokens tokens,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver, ClientOrigins origins) throws Exception
    {
        ProblemSecurityHandler problems = new ProblemSecurityHandler(resolver);
        http.securityMatcher(OPEN_API)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(origins))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .addFilterBefore(new OpenTokenFilter(tokens), AuthorizationFilter.class);
        return http.build();
    }
}
