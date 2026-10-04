// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth;

import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Minimal Spring Boot configuration so slice tests can bootstrap this module, with the entities of the modules it builds
 * on (accounts and tenants, OAuth clients, the audit trail) and the identity module's clock and password encoder.
 */
@SpringBootApplication(scanBasePackages = {"org.devlive.grantforge.oauth.domain"})
@AutoConfigurationPackage(basePackages = {"org.devlive.grantforge.oauth", "org.devlive.grantforge.authz", "org.devlive.grantforge.identity",
        "org.devlive.grantforge.audit"})
@Import(IdentityConfiguration.class)
class TestOAuthApplication
{
}
