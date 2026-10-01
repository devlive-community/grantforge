// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz;

import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Minimal Spring Boot configuration so slice tests can bootstrap this module; entities and repositories of the
 * identity module (accounts, tenants) and the audit trail are found too, with the identity module's clock.
 */
@SpringBootApplication(scanBasePackages = {"org.devlive.grantforge.authz", "org.devlive.grantforge.audit"})
@AutoConfigurationPackage(basePackages = {"org.devlive.grantforge.authz", "org.devlive.grantforge.identity",
        "org.devlive.grantforge.audit"})
@Import(IdentityConfiguration.class)
class TestAuthzApplication
{
}
