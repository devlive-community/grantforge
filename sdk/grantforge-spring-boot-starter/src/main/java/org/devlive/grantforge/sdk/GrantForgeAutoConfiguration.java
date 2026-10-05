// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Sets GrantForge up once {@code grantforge.client.base-url} is configured: the client, the checks, and in Spring MVC
 * applications the {@link RequirePermission} guard with problem-detail answers. Each bean can be replaced.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "grantforge.client", name = "base-url")
@EnableConfigurationProperties(GrantForgeProperties.class)
public class GrantForgeAutoConfiguration
{
    /**
     * The client of GrantForge's open API.
     *
     * @param properties where GrantForge is and how long answers are kept
     * @return the client
     */
    @Bean
    @ConditionalOnMissingBean
    public GrantForgeClient grantForgeClient(GrantForgeProperties properties)
    {
        return new GrantForgeClient(http(properties), properties.cacheTtl(), properties.cacheSize(), Clock.systemUTC());
    }

    /** A client of GrantForge's address with the configured timeout. */
    static RestClient http(GrantForgeProperties properties)
    {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(properties.timeout());
        requests.setReadTimeout(properties.timeout());
        return RestClient.builder().baseUrl(requireNonNull(properties.baseUrl(), "baseUrl").toString()).requestFactory(requests).build();
    }

    /**
     * Finds the current user's token in requests that carry none: none.
     *
     * @return a resolver for applications without servlets
     */
    @Bean
    @ConditionalOnMissingBean
    public AccessTokenResolver accessTokenResolver()
    {
        return () -> null;
    }

    /**
     * Checks in code.
     *
     * @param client asks GrantForge
     * @param tokens finds the current user's token
     * @return the checks
     */
    @Bean
    @ConditionalOnMissingBean
    public GrantForge grantForge(GrantForgeClient client, AccessTokenResolver tokens)
    {
        return new GrantForge(client, tokens);
    }

    /** JPA applications: data permissions as Specifications, and their entities declared at start-up. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({Specification.class, EntityManagerFactory.class})
    static class DataPermissions
    {
        /**
         * Limits queries to the rows the current user may use.
         *
         * @param client asks GrantForge
         * @param tokens finds the current user's token
         * @return the scopes
         */
        @Bean
        @ConditionalOnMissingBean
        GrantForgeDataScopes grantForgeDataScopes(GrantForgeClient client, AccessTokenResolver tokens)
        {
            return new GrantForgeDataScopes(client, tokens, Clock.systemUTC());
        }

        /**
         * Declares the annotated entities of the JPA metamodel once the application is ready, if the client's credentials
         * are configured.
         *
         * @param properties where GrantForge is and the client's credentials
         * @param factories the application's persistence units
         * @return the listener
         */
        @Bean
        @ConditionalOnProperty(prefix = "grantforge.client", name = {"client-id", "client-secret"})
        ApplicationListener<ApplicationReadyEvent> grantForgeEntityDeclaration(GrantForgeProperties properties,
                ObjectProvider<EntityManagerFactory> factories)
        {
            return event -> {
                List<Class<?>> types = factories.orderedStream().flatMap(factory -> factory.getMetamodel().getEntities().stream())
                        .<Class<?>>map(EntityType::getJavaType).collect(Collectors.toCollection(ArrayList::new));
                List<Class<?>> annotated = DataEntityRegistrar.annotated(types);
                if (!annotated.isEmpty()) {
                    new DataEntityRegistrar(http(properties), requireNonNull(properties.clientId(), "clientId"),
                            requireNonNull(properties.clientSecret(), "clientSecret")).declare(annotated);
                }
            };
        }
    }

    /** Servlet applications: tokens from the {@code Authorization} header. Declared first, so it wins over the default. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletTokens
    {
        /**
         * Reads bearer tokens of requests.
         *
         * @return the resolver
         */
        @Bean
        @ConditionalOnMissingBean
        AccessTokenResolver bearerTokenResolver()
        {
            return new BearerTokenResolver();
        }
    }

    /** Spring MVC applications: the guard of {@link RequirePermission}. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(HandlerInterceptor.class)
    static class MvcGuard
    {
        /**
         * Registers the guard for every handler.
         *
         * @param grantForge the checks
         * @return the configurer
         */
        @Bean
        WebMvcConfigurer grantForgeGuard(GrantForge grantForge)
        {
            RequirePermissionInterceptor interceptor = new RequirePermissionInterceptor(grantForge);
            return new WebMvcConfigurer()
            {
                @Override
                public void addInterceptors(InterceptorRegistry registry)
                {
                    registry.addInterceptor(interceptor);
                }
            };
        }

        /**
         * Answers refusals as problem details.
         *
         * @return the handler
         */
        @Bean
        @ConditionalOnMissingBean
        GrantForgeProblems grantForgeProblems()
        {
            return new GrantForgeProblems();
        }
    }
}
