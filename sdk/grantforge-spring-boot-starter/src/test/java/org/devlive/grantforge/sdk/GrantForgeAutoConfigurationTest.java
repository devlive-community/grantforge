// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationListener;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class GrantForgeAutoConfigurationTest
{
    private static final AutoConfigurations CONFIGURATION = AutoConfigurations.of(GrantForgeAutoConfiguration.class);

    @Test
    void staysOffWithoutGrantForgesAddress()
    {
        new ApplicationContextRunner().withConfiguration(CONFIGURATION).run(context -> assertThat(context).doesNotHaveBean(GrantForge.class));
    }

    @Test
    void setsUpTheClientAndInWebApplicationsTheGuard()
    {
        new ApplicationContextRunner().withConfiguration(CONFIGURATION).withPropertyValues("grantforge.client.base-url=https://gf.example")
                .run(context -> {
                    assertThat(context).hasSingleBean(GrantForge.class).hasSingleBean(GrantForgeClient.class)
                            .doesNotHaveBean(GrantForgeProblems.class).hasSingleBean(GrantForgeDataScopes.class);
                    // Without the client's credentials, nothing is declared.
                    assertThat(context.getBeanNamesForType(ApplicationListener.class))
                            .doesNotContain("grantForgeEntityDeclaration");
                    assertThat(context.getBean(AccessTokenResolver.class).currentToken()).isNull();
                });
        new WebApplicationContextRunner().withConfiguration(CONFIGURATION)
                .withPropertyValues("grantforge.client.base-url=https://gf.example", "grantforge.client.cache-ttl=10s")
                .run(context -> {
                    assertThat(context).hasSingleBean(GrantForgeProblems.class).hasSingleBean(WebMvcConfigurer.class);
                    assertThat(context.getBean(AccessTokenResolver.class)).isInstanceOf(BearerTokenResolver.class);
                    assertThat(context.getBean(GrantForgeProperties.class).cacheTtl()).hasSeconds(10);
                });
    }

    @Test
    void declaresEntitiesOnlyWithTheClientsCredentials()
    {
        new ApplicationContextRunner().withConfiguration(CONFIGURATION).withPropertyValues("grantforge.client.base-url=https://gf.example",
                "grantforge.client.client-id=gf_a", "grantforge.client.client-secret=s3")
                .run(context -> assertThat(context).hasBean("grantForgeEntityDeclaration"));
    }

    @Test
    void givesWayToTheApplicationsOwnBeans()
    {
        new ApplicationContextRunner().withConfiguration(CONFIGURATION).withPropertyValues("grantforge.client.base-url=https://gf.example")
                .withBean(AccessTokenResolver.class, () -> () -> "session-token")
                .run(context -> assertThat(context.getBean(AccessTokenResolver.class).currentToken()).isEqualTo("session-token"));
    }
}
