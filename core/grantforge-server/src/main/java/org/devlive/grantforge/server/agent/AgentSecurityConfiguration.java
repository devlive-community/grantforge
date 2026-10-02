// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.server.security.ProblemSecurityHandler;
import org.devlive.grantforge.service.agent.AgentTokens;
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
 * The filter chain of the agent API ({@value #AGENT_API}): stateless, without sessions or CSRF tokens, signed in only by
 * agent tokens. Console sessions do not reach it, and agent tokens reach nothing else.
 */
@Configuration(proxyBeanMethods = false)
public class AgentSecurityConfiguration
{
    /** The paths agents call. */
    public static final String AGENT_API = "/api/v1/agent/**";

    /**
     * The filter chain for agents, ahead of the console's.
     *
     * @param http the builder
     * @param tokens signs agents in
     * @param resolver MVC's exception resolver, to render rejections as problem details
     * @return the chain
     * @throws Exception if the configuration is invalid
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain agentFilterChain(HttpSecurity http, AgentTokens tokens,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
            throws Exception
    {
        ProblemSecurityHandler problems = new ProblemSecurityHandler(resolver);
        http.securityMatcher(AGENT_API)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .addFilterBefore(new AgentTokenFilter(tokens), AuthorizationFilter.class);
        return http.build();
    }
}
