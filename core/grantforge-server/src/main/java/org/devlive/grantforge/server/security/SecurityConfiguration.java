// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.identity.application.ConsoleSessionService;
import org.devlive.grantforge.identity.application.SessionProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Clock;
import java.util.List;

/**
 * Security of the HTTP API (D-10): the console signs in through {@code POST /api/v1/auth/login} and then
 * uses the session cookie; state-changing requests carry the {@code XSRF-TOKEN} cookie value in the
 * {@code X-XSRF-TOKEN} header. The console's static files, the API contract, health probes and the
 * pre-sign-in endpoints are public; every other API requires a session. Prometheus metrics require a
 * session unless {@code grantforge.observability.prometheus-public=true} (for scrapers on a trusted network).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration
{
    /** Name of the session cookie. */
    public static final String SESSION_COOKIE = "GRANTFORGE_SESSION";

    /**
     * The API routes anyone may call, as {@code METHOD path}. They must be exactly the endpoints marked
     * {@code @PublicEndpoint}; a test compares the two.
     */
    static final List<String> PUBLIC_ROUTES = List.of("GET /api/v1/bootstrap", "POST /api/v1/setup", "POST /api/v1/register",
            "POST /api/v1/auth/login", "POST /api/v1/auth/mfa", "POST /api/v1/auth/logout");

    /**
     * Shapes the session cookie the same way whatever the deployment (embedded server, test, war): HttpOnly,
     * SameSite=Lax, and Secure on HTTPS requests or always when {@code grantforge.security.cookie-secure} is set.
     *
     * @param alwaysSecure whether to mark the cookie Secure even on plain HTTP requests (TLS ends at a proxy)
     * @return the customizer Spring Boot applies to Spring Session's cookie serializer
     */
    @Bean
    public DefaultCookieSerializerCustomizer sessionCookie(
            @Value("${grantforge.security.cookie-secure:false}") boolean alwaysSecure)
    {
        return serializer -> {
            serializer.setCookieName(SESSION_COOKIE);
            serializer.setUseHttpOnlyCookie(true);
            serializer.setSameSite("Lax");
            serializer.setCookiePath("/");
            if (alwaysSecure) {
                serializer.setUseSecureCookie(true);
            }
        };
    }

    /**
     * Stores the CSRF token in a cookie the console can read (it is not a secret from the page itself).
     *
     * @return the repository
     */
    @Bean
    public CsrfTokenRepository csrfTokenRepository()
    {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    /**
     * Keeps the authentication in the (database-backed) HTTP session.
     *
     * @return the repository
     */
    @Bean
    public SecurityContextRepository securityContextRepository()
    {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * What happens to the session at sign-in: a new session ID (against session fixation) and a new CSRF token.
     *
     * @param csrfTokens the CSRF token repository
     * @return the strategy
     */
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokens)
    {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokens)));
    }

    /**
     * The filter chain for every request.
     *
     * @param http the builder
     * @param csrfTokens the CSRF token repository
     * @param contexts the security context repository
     * @param resolver MVC's exception resolver, to render rejections as problem details
     * @param prometheusPublic whether the metrics endpoint is public
     * @param consoleSessions records session activity
     * @param sessionProperties how often activity is recorded
     * @param clock source of the current time
     * @return the chain
     * @throws Exception if the configuration is invalid
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokens,
            SecurityContextRepository contexts, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
            @Value("${grantforge.observability.prometheus-public:false}") boolean prometheusPublic,
            ConsoleSessionService consoleSessions, SessionProperties sessionProperties, Clock clock)
            throws Exception
    {
        ProblemSecurityHandler problems = new ProblemSecurityHandler(resolver);
        http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
                .securityContext(context -> context.securityContextRepository(contexts))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(requests -> {
                    for (String route : PUBLIC_ROUTES) {
                        String[] parts = route.split(" ", 2);
                        requests.requestMatchers(HttpMethod.valueOf(parts[0]), parts[1]).permitAll();
                    }
                    requests.requestMatchers("/api/**").authenticated()
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    if (prometheusPublic) {
                        requests.requestMatchers("/actuator/prometheus").permitAll();
                    }
                    requests.requestMatchers("/actuator/**").authenticated()
                            .anyRequest().permitAll();
                })
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilterAfter(new TenantBindingFilter(), AuthorizationFilter.class)
                .addFilterAfter(new SessionActivityFilter(consoleSessions, sessionProperties.activityInterval(), clock),
                        TenantBindingFilter.class);
        return http.build();
    }
}
